// lib/commands/receive_command.dart

import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;

class ReceiveCommand {
  static const _green  = '\x1B[92m';
  static const _yellow = '\x1B[93m';
  static const _red    = '\x1B[91m';
  static const _dim    = '\x1B[2m';
  static const _bold   = '\x1B[1m';
  static const _reset  = '\x1B[0m';

  final String baseUrl =
      Platform.environment['ZSAFER_SERVER'] ?? 'http://localhost:8080';

  Future<void> run() async {
    stdout.writeln('\n${_bold}[ RECEIVE ]$_reset');

    // ── Key ──────────────────────────────────────────────────────────────────
    stdout.write('  ${_yellow}Key:$_reset      ');
    final key = (stdin.readLineSync() ?? '').trim();
    if (key.isEmpty) {
      stdout.writeln('  ${_red}✗ Key cannot be empty.$_reset');
      return;
    }

    // ── Password ─────────────────────────────────────────────────────────────
    stdout.write('  ${_yellow}Password (blank if none):$_reset ');
    _setEcho(false);
    final password = (stdin.readLineSync() ?? '').trim();
    _setEcho(true);
    stdout.writeln();

    // ── Download ─────────────────────────────────────────────────────────────
    stdout.write('\n  ${_dim}Fetching...$_reset');

    try {
      final uri = Uri.parse('$baseUrl/secret/$key').replace(
        queryParameters: password.isNotEmpty ? {'password': password} : null,
      );

      final response = await http.get(uri).timeout(const Duration(minutes: 5));

      if (response.statusCode != 200) {
        stdout.writeln(
            '\r  ${_red}✗ ${response.statusCode}: ${response.body.substring(0, response.body.length.clamp(0, 200))}$_reset');
        return;
      }

      final contentType = response.headers['content-type'] ?? 'application/octet-stream';
      final disposition = response.headers['content-disposition'] ?? '';
      final filename    = _extractFilename(disposition, key);
      final bytes       = response.bodyBytes;

      final kb = (bytes.length / 1024).toStringAsFixed(0);
      stdout.writeln('\r  ${_green}✓ Received: $filename (${kb} KB)$_reset');
      stdout.writeln('  ${_dim}Launching protected viewer...$_reset\n');

      await _launchViewer(bytes, filename, contentType);
    } on SocketException {
      stdout.writeln('\r  ${_red}✗ Cannot reach server at $baseUrl$_reset');
    } catch (e) {
      stdout.writeln('\r  ${_red}✗ Error: $e$_reset');
    }
  }

  // ── Launch protected viewer in a fresh process ────────────────────────────
  // We re-launch the zsafer binary itself with --viewer mode.
  // The decrypted bytes are passed as base64 in a JSON payload so
  // no temp file is ever written to disk.
  Future<void> _launchViewer(
      List<int> bytes, String filename, String mimeType) async {
    final payload = jsonEncode({
      'data':     base64.encode(bytes),
      'filename': filename,
      'mime':     mimeType,
    });
    final encoded = base64.encode(utf8.encode(payload));

    // Find own executable path
    final selfPath = Platform.resolvedExecutable;

    // Launch viewer as separate process so terminal is still usable
    await Process.start(
      selfPath,
      ['--viewer', encoded],
      mode: ProcessStartMode.detached,
    );
  }

  String _extractFilename(String disposition, String key) {
    if (disposition.contains('filename=')) {
      return disposition
          .split('filename=')
          .last
          .trim()
          .replaceAll('"', '');
    }
    return 'received_${key.substring(0, 8)}';
  }

  void _setEcho(bool enabled) {
    try {
      Process.runSync('stty', enabled ? ['echo'] : ['-echo']);
    } catch (_) {}
  }
}
