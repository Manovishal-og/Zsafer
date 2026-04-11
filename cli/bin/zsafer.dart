// bin/zsafer.dart — entry point
//
// CLI mode:   zsafer send | zsafer receive
// Viewer mode (internal): zsafer --viewer <base64-json>

import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';

import '../lib/commands/send_command.dart';
import '../lib/commands/receive_command.dart';
import '../lib/viewer/viewer_main.dart';

void main(List<String> args) async {
  // ── VIEWER MODE ─────────────────────────────────────────────────────────
  // Launched internally by ReceiveCommand with a base64-encoded JSON payload.
  if (args.isNotEmpty && args[0] == '--viewer') {
    WidgetsFlutterBinding.ensureInitialized();
    MediaKit.ensureInitialized();

    if (args.length < 2) {
      stderr.writeln('[zsafer] Missing viewer payload');
      exit(1);
    }

    final Map<String, dynamic> payload =
        jsonDecode(utf8.decode(base64.decode(args[1])));

    final bytes    = base64.decode(payload['data']     as String);
    final filename = payload['filename']               as String;
    final mime     = payload['mime']                   as String;

    runApp(ViewerApp(bytes: bytes, filename: filename, mimeType: mime));
    return;
  }

  // ── CLI MODE ─────────────────────────────────────────────────────────────
  _printBanner();

  if (args.isEmpty || !['send', 'receive'].contains(args[0])) {
    _printUsage();
    exit(0);
  }

  if (args[0] == 'send') {
    await SendCommand().run();
  } else {
    await ReceiveCommand().run();
  }
}

void _printBanner() {
  stdout.writeln('''
\x1B[96m
  ███████╗███████╗ █████╗ ███████╗███████╗██████╗ 
  ╚══███╔╝██╔════╝██╔══██╗██╔════╝██╔════╝██╔══██╗
    ███╔╝ ███████╗███████║█████╗  █████╗  ██████╔╝
   ███╔╝  ╚════██║██╔══██║██╔══╝  ██╔══╝  ██╔══██╗
  ███████╗███████║██║  ██║██║     ███████╗██║  ██║
  ╚══════╝╚══════╝╚═╝  ╚═╝╚═╝     ╚══════╝╚═╝  ╚═╝
\x1B[0m\x1B[2m  Ephemeral file sharing — files that burn after viewing\x1B[0m
''');
}

void _printUsage() {
  stdout.writeln('  Usage:');
  stdout.writeln('    \x1B[92mzsafer send\x1B[0m     → share a file');
  stdout.writeln('    \x1B[92mzsafer receive\x1B[0m  → receive and view a file');
  stdout.writeln();
  stdout.writeln('  ZSAFER_SERVER env var overrides server (default http://localhost:8080)');
  stdout.writeln();
}
