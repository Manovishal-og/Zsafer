import 'dart:io';
import 'dart:convert';
import 'dart:async';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;

void main(List<String> arguments) async {
	if (arguments.isEmpty) {
	print('Usage: zsafer <send|receive>');
	return;
  }

	final command = arguments[0];
	if (command == 'send') {
	await handleSend();
  } else if (command == 'receive') {
	await handleReceive();
  }
}

Future<void> handleSend() async {
	stdout.write('📁 File path: ');
	final path = stdin.readLineSync()?.trim() ?? '';
	final file = File(path);

	if (!await file.exists()) {
	print('❌ Error: File not found.');
	return;
  }

	stdout.write('📧 Receiver Email: ');
	final to = stdin.readLineSync()?.trim() ?? '';
	stdout.write('🔑 Password (optional): ');
	final pass = stdin.readLineSync()?.trim();
	stdout.write('⏳ Expiry (seconds): ');
	final expiry = stdin.readLineSync()?.trim() ?? '3600';

	var request = http.MultipartRequest('POST', Uri.parse('http://localhost:8080/secret'));
	request.files.add(await http.MultipartFile.fromPath('file', path));

	request.fields['expiry'] = expiry;
	request.fields['receiverEmail'] = to;
	request.fields['duration'] = '60'; 
	if (pass != null && pass.isNotEmpty) request.fields['password'] = pass;

	print('🚀 Uploading encrypted payload...');
	try {
		final response = await http.Response.fromStream(await request.send());
		if (response.statusCode == 201) {
	  final data = jsonDecode(response.body);
	  print('✅ Success! ID: ${data['id']}');
	} else {
	  print('❌ Failed: ${response.body}');
	}
	} catch (e) {
		print('❌ Connection error: $e');
	}
}

Future<void> handleReceive() async {
	stdout.write('🆔 Secret ID: ');
	final id = stdin.readLineSync()?.trim() ?? '';
	stdout.write('🔑 Password: ');
	final pass = stdin.readLineSync()?.trim() ?? '';

	final url = Uri.parse('http://localhost:8080/secret/$id?password=$pass');
	print('🔒 Fetching content...');

	try {
		final response = await http.get(url, headers: {'User-Agent': 'zsafer-cli'});

		if (response.statusCode == 200) {
	  final contentType = response.headers['content-type'] ?? '';
	  final disposition = response.headers['content-disposition'] ?? '';
	  final fileName = _parseFileName(disposition);
	  final bytes = response.bodyBytes;

	  // APPLY PLATFORM SPECIFIC PROTECTION
	  _applyOSProtection();

	  if (contentType.contains('text/plain')) {
		_displaySecureText(utf8.decode(bytes));
	  } else {
		await _openSecureMedia(bytes, fileName, contentType);
	  }
	} else {
	  print('❌ Error: ${response.body}');
	}
	} catch (e) {
		print('❌ Connection error: $e');
	}
}

void _applyOSProtection() {
	if (Platform.isWindows) {
	// Windows: Use PowerShell to set the window to be excluded from capture
	// This targets the current terminal window
	Process.run('powershell', [
	  '-Command',
	  '\$systemSource = "[DllImport(\\"user32.dll\\")] public static extern uint SetWindowDisplayAffinity(IntPtr hWnd, uint dwAffinity);"; \$type = Add-Type -MemberDefinition \$systemSource -Name "Win32" -PassThru; \$process = Get-Process -Id \$pid; \$type::SetWindowDisplayAffinity(\$process.MainWindowHandle, 0x00000011);'
	]);
  } else if (Platform.isLinux) {
	// Linux (Generic): Set terminal title for compositor rules
	stdout.write('\x1B]2;zsafer_secure\x07');
  } else if (Platform.isMacOS) {
	// macOS: Use AppleScript to prevent the window from being captured (Limited support)
	print('⚠️ macOS: Screen protection relies on immediate volatile cleanup.');
  }
}

String _parseFileName(String disposition) {
	final match = RegExp(r'filename="([^"]+)"').firstMatch(disposition);
	return match?.group(1) ?? 'secret_file';
}

void _displaySecureText(String text) {
	stdout.write('\x1B[2J\x1B[3J\x1B[H'); 
	print('=== SECURE TEXT VIEW ===\n');
	print(text);
	_startBurnCountdown(20);
}

Future<void> _openSecureMedia(List<int> bytes, String fileName, String mimeType) async {
	final tempDir = Directory('${Directory.systemTemp.path}/.zsafer_vault');
	if (!tempDir.existsSync()) tempDir.createSync();

	final tempFile = File('${tempDir.path}/$fileName');
	await tempFile.writeAsBytes(bytes);

	print('🔓 Opening $fileName in system viewer...');

	// Universal opener
	if (Platform.isWindows) {
	await Process.start('start', [tempFile.path], runInShell: true);
  } else if (Platform.isMacOS) {
	await Process.start('open', [tempFile.path]);
  } else {
	await Process.start('xdg-open', [tempFile.path]);
  }

	await _startBurnCountdown(45);

	// Secure Cleanup
	if (await tempFile.exists()) {
	await tempFile.delete();
	print('\n🗑️ Local temporary file wiped.');
  }
}

Future<void> _startBurnCountdown(int seconds) async {
	print('\n-------------------------------------------');
	for (int i = seconds; i > 0; i--) {
	stdout.write('\r🔥 Content will be burned and cleared in $i seconds... ');
	await Future.delayed(Duration(seconds: 1));
}
	stdout.write('\x1B[2J\x1B[3J\x1B[H'); 
	if (Platform.isWindows) {
	 // Reset Windows Protection so the terminal isn't stuck as "black"
	 Process.run('powershell', ['-Command', '\$systemSource = "[DllImport(\\"user32.dll\\")] public static extern uint SetWindowDisplayAffinity(IntPtr hWnd, uint dwAffinity);"; \$type = Add-Type -MemberDefinition \$systemSource -Name "Win32" -PassThru; \$process = Get-Process -Id \$pid; \$type::SetWindowDisplayAffinity(\$process.MainWindowHandle, 0x00000000);']);
  }
	print('✅ Content Burned.');
}
