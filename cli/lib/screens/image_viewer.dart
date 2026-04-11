// lib/viewer/screens/image_viewer.dart
//
// Displays images (jpg, png, gif, webp) from raw bytes.
// Supports pinch-to-zoom and pan via InteractiveViewer.
// No file is written to disk — bytes stay in memory.

import 'dart:typed_data';
import 'package:flutter/material.dart';

class ImageViewer extends StatelessWidget {
  final List<int> bytes;
  final String    filename;

  const ImageViewer({super.key, required this.bytes, required this.filename});

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // ── Zoom + pan ─────────────────────────────────────────────────────
        InteractiveViewer(
          minScale: 0.5,
          maxScale: 8.0,
          child: Center(
            child: Image.memory(
              Uint8List.fromList(bytes),
              fit:          BoxFit.contain,
              errorBuilder: (_, error, __) => _ErrorWidget(message: '$error'),
            ),
          ),
        ),
        // ── Filename badge ─────────────────────────────────────────────────
        Positioned(
          top:  12,
          left: 12,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color:        Colors.black54,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              filename,
              style: const TextStyle(color: Colors.white70, fontSize: 12),
            ),
          ),
        ),
        // ── Hint ───────────────────────────────────────────────────────────
        const Positioned(
          bottom: 12,
          right:  12,
          child:  Text(
            'Scroll to zoom · Drag to pan',
            style: TextStyle(color: Colors.white38, fontSize: 11),
          ),
        ),
      ],
    );
  }
}

class _ErrorWidget extends StatelessWidget {
  final String message;
  const _ErrorWidget({required this.message});

  @override
  Widget build(BuildContext context) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.broken_image_outlined, color: Colors.white38, size: 64),
            const SizedBox(height: 12),
            Text(
              'Cannot display image\n$message',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white38, fontSize: 13),
            ),
          ],
        ),
      );
}
