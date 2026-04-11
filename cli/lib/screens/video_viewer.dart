// lib/viewer/screens/video_viewer.dart
//
// Plays video and audio entirely in-memory using media_kit.
// A temp file is written, but deleted when the viewer closes.
// Screenshot protection via _NET_WM_BYPASS_COMPOSITOR is applied
// by viewer_main.dart at the window level (covers the whole window).

import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:path/path.dart' as p;

class VideoViewer extends StatefulWidget {
  final List<int> bytes;
  final String    filename;
  final String    mimeType;

  const VideoViewer({
    super.key,
    required this.bytes,
    required this.filename,
    required this.mimeType,
  });

  @override
  State<VideoViewer> createState() => _VideoViewerState();
}

class _VideoViewerState extends State<VideoViewer> {
  late final Player      _player;
  late final VideoController _controller;
  String? _tempPath;
  bool    _ready = false;

  @override
  void initState() {
    super.initState();
    _player     = Player();
    _controller = VideoController(_player);
    _initPlayback();
  }

  Future<void> _initPlayback() async {
    // Write bytes to a secure temp file — deleted on close
    final tmpDir  = Directory.systemTemp;
    final ext     = p.extension(widget.filename);
    final tmpFile = File(p.join(tmpDir.path, 'zsafer_${_randomId()}$ext'));
    await tmpFile.writeAsBytes(Uint8List.fromList(widget.bytes));
    _tempPath = tmpFile.path;

    await _player.open(Media(_tempPath!));
    if (mounted) setState(() => _ready = true);
  }

  String _randomId() =>
      DateTime.now().millisecondsSinceEpoch.toRadixString(36);

  @override
  void dispose() {
    _player.dispose();
    // Delete temp file
    if (_tempPath != null) {
      try { File(_tempPath!).deleteSync(); } catch (_) {}
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_ready) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(color: Color(0xFF00E5CC)),
            SizedBox(height: 16),
            Text('Decrypting & buffering...',
                style: TextStyle(color: Colors.white54, fontSize: 13)),
          ],
        ),
      );
    }

    final isAudio = widget.mimeType.startsWith('audio/');

    return Column(
      children: [
        // ── Video area ───────────────────────────────────────────────────
        Expanded(
          child: isAudio ? _AudioVisual() : Video(controller: _controller),
        ),
        // ── Controls ─────────────────────────────────────────────────────
        _Controls(player: _player, filename: widget.filename),
      ],
    );
  }
}

// ── Audio-only visual placeholder ────────────────────────────────────────────
class _AudioVisual extends StatelessWidget {
  @override
  Widget build(BuildContext context) => const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.audiotrack, color: Color(0xFF00E5CC), size: 80),
            SizedBox(height: 16),
            Text('Audio File',
                style: TextStyle(color: Colors.white54, fontSize: 16)),
          ],
        ),
      );
}

// ── Playback controls ─────────────────────────────────────────────────────────
class _Controls extends StatefulWidget {
  final Player player;
  final String filename;
  const _Controls({required this.player, required this.filename});

  @override
  State<_Controls> createState() => _ControlsState();
}

class _ControlsState extends State<_Controls> {
  bool _playing = true;
  double _volume = 1.0;
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;

  @override
  void initState() {
    super.initState();
    widget.player.stream.playing.listen(
        (v) => mounted ? setState(() => _playing = v) : null);
    widget.player.stream.position.listen(
        (v) => mounted ? setState(() => _position = v) : null);
    widget.player.stream.duration.listen(
        (v) => mounted ? setState(() => _duration = v) : null);
  }

  String _fmt(Duration d) =>
      '${d.inMinutes.toString().padLeft(2, '0')}:'
      '${(d.inSeconds % 60).toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final progress = (_duration.inMilliseconds > 0)
        ? _position.inMilliseconds / _duration.inMilliseconds
        : 0.0;

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      color:   const Color(0xFF0A0A10),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // ── Seek bar ────────────────────────────────────────────────────
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              thumbShape:      const RoundSliderThumbShape(enabledThumbRadius: 6),
              overlayShape:    const RoundSliderOverlayShape(overlayRadius: 12),
              trackHeight:     3,
              activeTrackColor:   const Color(0xFF00E5CC),
              inactiveTrackColor: const Color(0xFF2A2A35),
              thumbColor:      const Color(0xFF00E5CC),
              overlayColor:    const Color(0x2200E5CC),
            ),
            child: Slider(
              value:   progress.clamp(0.0, 1.0),
              onChanged: (v) {
                if (_duration == Duration.zero) return;
                widget.player.seek(
                    Duration(milliseconds: (v * _duration.inMilliseconds).round()));
              },
            ),
          ),
          // ── Buttons row ──────────────────────────────────────────────────
          Row(
            children: [
              // Play / Pause
              IconButton(
                icon: Icon(
                  _playing ? Icons.pause_circle : Icons.play_circle,
                  color: const Color(0xFF00E5CC),
                  size:  32,
                ),
                onPressed: widget.player.playOrPause,
              ),
              // Time
              Text(
                '${_fmt(_position)} / ${_fmt(_duration)}',
                style: const TextStyle(color: Colors.white54, fontSize: 12),
              ),
              const Spacer(),
              // Filename
              Text(
                widget.filename,
                style: const TextStyle(color: Colors.white38, fontSize: 11),
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(width: 12),
              // Volume icon
              Icon(
                _volume > 0 ? Icons.volume_up : Icons.volume_off,
                color: Colors.white54,
                size:  18,
              ),
              // Volume slider
              SizedBox(
                width: 80,
                child: SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    thumbShape:    const RoundSliderThumbShape(enabledThumbRadius: 5),
                    trackHeight:   2,
                    activeTrackColor:   const Color(0xFF00E5CC),
                    inactiveTrackColor: const Color(0xFF2A2A35),
                    thumbColor:    const Color(0xFF00E5CC),
                  ),
                  child: Slider(
                    value:   _volume,
                    onChanged: (v) {
                      setState(() => _volume = v);
                      widget.player.setVolume(v * 100);
                    },
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
