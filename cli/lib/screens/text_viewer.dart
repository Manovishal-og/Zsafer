// lib/viewer/screens/text_viewer.dart
//
// Displays text files and provides a best-effort view of other document types.
// Nothing is written to disk.

import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class TextViewer extends StatefulWidget {
  final List<int> bytes;
  final String    filename;
  final String    mimeType;

  const TextViewer({
    super.key,
    required this.bytes,
    required this.filename,
    required this.mimeType,
  });

  @override
  State<TextViewer> createState() => _TextViewerState();
}

class _TextViewerState extends State<TextViewer> {
  late final String _content;
  late final bool   _isPlainText;
  final _scrollCtrl = ScrollController();

  @override
  void initState() {
    super.initState();
    _isPlainText = widget.mimeType.startsWith('text/') ||
        widget.filename.endsWith('.txt') ||
        widget.filename.endsWith('.md')  ||
        widget.filename.endsWith('.csv') ||
        widget.filename.endsWith('.json');

    if (_isPlainText) {
      _content = utf8.decode(widget.bytes, allowMalformed: true);
    } else {
      // For PDFs / docx / unknown: show a notice and hex preview
      _content = _buildHexNotice();
    }
  }

  String _buildHexNotice() {
    final sb = StringBuffer();
    sb.writeln('━━━  Binary file: ${widget.filename}  ━━━');
    sb.writeln('MIME type: ${widget.mimeType}');
    sb.writeln('Size: ${(widget.bytes.length / 1024).toStringAsFixed(1)} KB');
    sb.writeln();
    sb.writeln(
        'This file type cannot be rendered inline in the protected viewer.');
    sb.writeln('The file data is in memory and will be discarded when you close.');
    sb.writeln();
    sb.writeln('Hex preview (first 512 bytes):');
    sb.writeln('─' * 52);

    final preview = widget.bytes.take(512).toList();
    for (int i = 0; i < preview.length; i += 16) {
      final row = preview.skip(i).take(16).toList();
      final hex = row.map((b) => b.toRadixString(16).padLeft(2, '0')).join(' ');
      final asc = row.map((b) => (b >= 32 && b < 127) ? String.fromCharCode(b) : '.').join();
      sb.writeln('${i.toRadixString(16).padLeft(4, '0')}  ${hex.padRight(48)}  $asc');
    }
    return sb.toString();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // ── Toolbar ─────────────────────────────────────────────────────────
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          color:   const Color(0xFF13131A),
          child: Row(
            children: [
              const Icon(Icons.description_outlined,
                  color: Color(0xFF00E5CC), size: 16),
              const SizedBox(width: 8),
              Text(
                widget.filename,
                style: const TextStyle(
                    color: Color(0xFF00E5CC),
                    fontSize: 13,
                    fontWeight: FontWeight.w600),
              ),
              const Spacer(),
              if (_isPlainText)
                _ToolbarBtn(
                  label: 'Copy all',
                  icon:  Icons.copy,
                  onTap: () {
                    Clipboard.setData(ClipboardData(text: _content));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content:  Text('Copied to clipboard'),
                        duration: Duration(seconds: 2),
                      ),
                    );
                  },
                ),
            ],
          ),
        ),
        // ── Content ─────────────────────────────────────────────────────────
        Expanded(
          child: Scrollbar(
            controller: _scrollCtrl,
            thumbVisibility: true,
            child: SingleChildScrollView(
              controller: _scrollCtrl,
              padding:    const EdgeInsets.all(16),
              child: SelectableText(
                _content,
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize:   13,
                  color:      Color(0xFFD4D4D4),
                  height:     1.6,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _ToolbarBtn extends StatelessWidget {
  final String  label;
  final IconData icon;
  final VoidCallback onTap;

  const _ToolbarBtn({
    required this.label,
    required this.icon,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => TextButton.icon(
        onPressed: onTap,
        icon:  Icon(icon,  color: Colors.white54, size: 14),
        label: Text(label, style: const TextStyle(color: Colors.white54, fontSize: 12)),
      );
}
