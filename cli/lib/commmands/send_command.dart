// lib/commands/send_command.dart

import 'dart:io';
import 'package:http/http.dart' as http;
import 'dart:convert';

class SendCommand {
  static const _green  = '\x1B[92m';
  static const _yellow = '\x1B[93m';
  static const _red    = '\x1B[91m';
  static const _dim    = '\x1B[2m';
  static const _bold   = '\x1B[1m';
  static const _reset  = '\x1B[0m';

  final String baseUrl = Platform.environment['ZSAFER_SERVER'] ?? 'http://localhost:8080';

  static const _allowed = {'pdf', 'jpg', 'png', 'docx', 'mp3', 'mp4', 'txt'};

  Future<void> run() async {
    stdout.writeln('\n${_bold}[ SEND ]$_reset');

    // ── File path ────────────────────────────────────────────────────────────
    String filePath;
    while (true) {
      stdout.write('  ${_yellow}File path:$_reset ');
      filePath = (stdin.readLineSync() ?? '').trim();
      if (filePath.startsWith('~')) {
        filePath = filePath.replaceFirst('~', Platform.environment['HOME'] ?? '');
      }
      if (File(filePath).existsSync()) break;
      stdout.writeln('  ${_red}✗ File not found. Try again.$_reset');
    }

    final ext = filePath.contains('.')
        ? filePath.split('.').last.toLowerCase()
        : '';
    if (!_allowed.contains(ext)) {
      stdout.writeln('  ${_red}✗ Extension .$ext is not allowed.$_reset');
      stdout.writeln('  ${_dim}Allowed: ${_allowed.join(', ')}$_reset');
      return;
    }

    // ── Expiry ───────────────────────────────────────────────────────────────
    stdout.write('  ${_yellow}Expiry seconds [${_dim}86400 = 1 day$_reset${_yellow}]:$_reset ');
    final expiryRaw = (stdin.readLineSync() ?? '').trim();
    final expiry = int.tryParse(expiryRaw) != null ? expiryRaw : '86400';

    // ── Password ─────────────────────────────────────────────────────────────
    stdout.write('  ${_yellow}Password (blank = no password):$_reset ');
    // Disable echo for password
    _setEcho(false);
    final password = (stdin.readLineSync() ?? '').trim();
    _setEcho(true);
    stdout.writeln();

    // ── View window duration ─────────────────────────────────────────────────
    stdout.write('  ${_yellow}View window seconds [${_dim}300 for video, Enter = default$_reset${_yellow}]:$_reset ');
    final durationRaw = (stdin.readLineSync() ?? '').trim();

    // ── Optional emails ──────────────────────────────────────────────────────
    stdout.write('  ${_yellow}Receiver email     (optional):$_reset ');
    final receiverEmail = (stdin.readLineSync() ?? '').trim();

    stdout.write('  ${_yellow}Your email         (notified on view, optional):$_reset ');
    final senderEmail = (stdin.readLineSync() ?? '').trim();

    // ── Upload ───────────────────────────────────────────────────────────────
    stdout.write('\n  ${_dim}Uploading...$_reset');

    try {
      final request = http.MultipartRequest(
        'POST',
        Uri.parse('$baseUrl/secret'),
      );

      request.files.add(await http.MultipartFile.fromPath('file', filePath));
      request.fields['expiry'] = expiry;
      if (password.isNotEmpty)       request.fields['password']      = password;
      if (receiverEmail.isNotEmpty)  request.fields['receiverEmail'] = receiverEmail;
      if (senderEmail.isNotEmpty)    request.fields['senderEmail']   = senderEmail;
      if (int.tryParse(durationRaw) != null) {
        request.fields['duration'] = durationRaw;
      }

      final streamed = await request.send().timeout(const Duration(minutes: 5));
      final response = await http.Response.fromStream(streamed);

      if (response.statusCode == 201) {
        final j   = jsonDecode(response.body);
        final key = j['key'] ?? '';
        stdout.writeln('\r  ${_green}✓ Uploaded!$_reset                    ');
        stdout.writeln();
        stdout.writeln('  ${_bold}Key:$_reset      ${_green}$key$_reset');
        stdout.writeln('  ${_bold}URL:$_reset      ${_dim}${j['downloadUrl']}$_reset');
        stdout.writeln('  ${_bold}Expires:$_reset  ${j['expiresAt']}');
        stdout.writeln();
        stdout.writeln('  ${_yellow}Share this key with the receiver. They need: zsafer receive$_reset');
        stdout.writeln();
      } else {
        stdout.writeln('\r  ${_red}✗ Server error ${response.statusCode}:$_reset');
        stdout.writeln('  ${response.body.substring(0, response.body.length.clamp(0, 300))}');
      }
    } on SocketException {
      stdout.writeln('\r  ${_red}✗ Cannot reach server at $baseUrl$_reset');
    } catch (e) {
      stdout.writeln('\r  ${_red}✗ Error: $e$_reset');
    }
  }

  void _setEcho(bool enabled) {
    try {
      // Uses stty to toggle terminal echo (Linux/macOS)
      Process.runSync('stty', enabled ? ['echo'] : ['-echo']);
    } catch (_) {}
  }
}
