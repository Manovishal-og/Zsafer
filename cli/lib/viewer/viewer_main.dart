// lib/viewer/viewer_main.dart
//
// Root Flutter app for the protected content viewer.
// Routes content to the correct screen based on MIME type.

import 'dart:io';
import 'dart:isolate';

import 'package:flutter/material.dart';

import 'protection/screenshot_protection.dart';
import 'screens/image_viewer.dart';
import 'screens/text_viewer.dart';
import 'screens/video_viewer.dart';

const _windowTitle = 'zsafer — Protected Content';

class ViewerApp extends StatelessWidget {
  final List<int> bytes;
  final String    filename;
  final String    mimeType;

  const ViewerApp({
    super.key,
    required this.bytes,
    required this.filename,
    required this.mimeType,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title:        _windowTitle,
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF0D0D12),
        colorScheme: ColorScheme.dark(
          primary:   const Color(0xFF00E5CC),
          secondary: const Color(0xFF00E5CC),
          surface:   const Color(0xFF13131A),
        ),
      ),
      home: _ViewerShell(
        bytes:    bytes,
        filename: filename,
        mimeType: mimeType,
      ),
    );
  }
}

class _ViewerShell extends StatefulWidget {
  final List<int> bytes;
  final String    filename;
  final String    mimeType;

  const _ViewerShell({
    required this.bytes,
    required this.filename,
    required this.mimeType,
  });

  @override
  State<_ViewerShell> createState() => _ViewerShellState();
}

class _ViewerShellState extends State<_ViewerShell> {
  @override
  void initState() {
    super.initState();
    // Apply screenshot protection after window has rendered
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _applyProtection();
    });
  }

  void _applyProtection() {
    // Run in background isolate so UI doesn't stutter
    Isolate.run(() {
      applyByWindowTitle(_windowTitle);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          // ── Content area ────────────────────────────────────────────────
          Expanded(
            child: _routeContent(),
          ),
          // ── Warning bar ─────────────────────────────────────────────────
          _WarningBar(filename: widget.filename),
        ],
      ),
    );
  }

  Widget _routeContent() {
    final mime = widget.mimeType.split(';').first.trim().toLowerCase();

    if (mime.startsWith('video/') || mime.startsWith('audio/')) {
      return VideoViewer(bytes: widget.bytes, filename: widget.filename, mimeType: mime);
    }

    if (mime.startsWith('image/')) {
      return ImageViewer(bytes: widget.bytes, filename: widget.filename);
    }

    // text/*, application/pdf, application/msword, application/octet-stream etc.
    return TextViewer(bytes: widget.bytes, filename: widget.filename, mimeType: mime);
  }
}

// ── Bottom warning bar ────────────────────────────────────────────────────────
class _WarningBar extends StatelessWidget {
  final String filename;
  const _WarningBar({required this.filename});

  @override
  Widget build(BuildContext context) {
    return Container(
      width:   double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color:   const Color(0xFF1A0808),
      child: Row(
        children: [
          const Icon(Icons.security, color: Color(0xFFE05252), size: 16),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '⚠  Protected Content — Screenshots produce a black screen    [$filename]',
              style: const TextStyle(
                color:    Color(0xFFE05252),
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          TextButton(
            onPressed: () => exit(0),
            child: const Text(
              '[Q] Quit',
              style: TextStyle(color: Color(0xFFAAAAAA), fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}
