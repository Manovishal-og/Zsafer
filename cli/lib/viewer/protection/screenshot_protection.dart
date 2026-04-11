// lib/viewer/protection/screenshot_protection.dart
//
// Sets _NET_WM_BYPASS_COMPOSITOR = 1 on the viewer window.
// This tells X11 compositors (KWin, Mutter, Picom, Compiz) to stop
// compositing this window into their shared framebuffer.
// Screenshot tools (gnome-screenshot, flameshot, scrot, PrintScreen)
// capture the compositor's buffer → they get BLACK where our window is.
//
// Method 1 (primary):  dart:ffi → libX11 → XChangeProperty
// Method 2 (fallback): xprop shell command via xdotool window lookup

import 'dart:ffi';
import 'dart:io';
import 'package:ffi/ffi.dart';

// ── FFI type aliases ──────────────────────────────────────────────────────────
typedef DisplayPtr = Pointer<Void>;
typedef XOpenDisplayNative  = DisplayPtr Function(Pointer<Utf8>);
typedef XOpenDisplayDart    = DisplayPtr Function(Pointer<Utf8>);
typedef XDefaultScreenNative = Int32 Function(DisplayPtr);
typedef XDefaultScreenDart   = int    Function(DisplayPtr);
typedef XRootWindowNative   = Int64  Function(DisplayPtr, Int32);
typedef XRootWindowDart     = int    Function(DisplayPtr, int);
typedef XInternAtomNative   = Int64  Function(DisplayPtr, Pointer<Utf8>, Int32);
typedef XInternAtomDart     = int    Function(DisplayPtr, Pointer<Utf8>, int);
typedef XChangePropertyNative = Int32 Function(
    DisplayPtr, Int64, Int64, Int64, Int32, Int32, Pointer<Uint32>, Int32);
typedef XChangePropertyDart = int Function(
    DisplayPtr, int, int, int, int, int, Pointer<Uint32>, int);
typedef XSyncNative = Int32 Function(DisplayPtr, Int32);
typedef XSyncDart   = int   Function(DisplayPtr, int);

// ─────────────────────────────────────────────────────────────────────────────

/// Applies screenshot protection to the given X11 window id.
/// Returns true if successful.
bool applyX11ScreenshotProtection(int windowId) {
  try {
    return _applyViaFFI(windowId);
  } catch (_) {
    return _applyViaXprop(windowId);
  }
}

/// Applies protection by searching for a window with the given title.
/// Used when we don't have the window id yet.
bool applyByWindowTitle(String title) {
  // Try FFI first — requires knowing window id, so fall straight to xprop.
  return _applyViaXpropByTitle(title);
}

// ── Method 1: libX11 FFI ─────────────────────────────────────────────────────
bool _applyViaFFI(int windowId) {
  if (!Platform.isLinux) return false;

  final lib = DynamicLibrary.open('libX11.so.6');

  final xOpenDisplay = lib.lookupFunction<XOpenDisplayNative, XOpenDisplayDart>(
      'XOpenDisplay');
  final xDefaultScreen =
      lib.lookupFunction<XDefaultScreenNative, XDefaultScreenDart>(
          'XDefaultScreen');
  final xInternAtom =
      lib.lookupFunction<XInternAtomNative, XInternAtomDart>('XInternAtom');
  final xChangeProperty =
      lib.lookupFunction<XChangePropertyNative, XChangePropertyDart>(
          'XChangeProperty');
  final xSync =
      lib.lookupFunction<XSyncNative, XSyncDart>('XSync');

  final displayNamePtr = nullptr.cast<Utf8>(); // default display (:0)
  final display = xOpenDisplay(displayNamePtr);
  if (display == nullptr) return false;

  // _NET_WM_BYPASS_COMPOSITOR atom
  final atomName = '_NET_WM_BYPASS_COMPOSITOR'.toNativeUtf8();
  final cardinalName = 'CARDINAL'.toNativeUtf8();

  final bypassAtom  = xInternAtom(display, atomName,    0);
  final cardinalAtom = xInternAtom(display, cardinalName, 0);

  // value = 1 means "bypass compositor for this window"
  final valuePtr = calloc<Uint32>();
  valuePtr.value = 1;

  xChangeProperty(
    display,
    windowId,   // window
    bypassAtom, // property
    cardinalAtom, // type
    32,         // format (32-bit values)
    0,          // PropModeReplace
    valuePtr,
    1,          // number of elements
  );

  xSync(display, 0);

  calloc.free(valuePtr);
  malloc.free(atomName);
  malloc.free(cardinalName);

  return true;
}

// ── Method 2: xprop by window id ─────────────────────────────────────────────
bool _applyViaXprop(int windowId) {
  try {
    final result = Process.runSync('xprop', [
      '-id', windowId.toRadixString(16),
      '-f', '_NET_WM_BYPASS_COMPOSITOR', '32c',
      '-set', '_NET_WM_BYPASS_COMPOSITOR', '1',
    ]);
    return result.exitCode == 0;
  } catch (_) {
    return false;
  }
}

// ── Method 3: xdotool + xprop by window title ────────────────────────────────
bool _applyViaXpropByTitle(String title) {
  try {
    // Give the window ~800ms to appear
    sleep(const Duration(milliseconds: 800));

    final search = Process.runSync('xdotool', ['search', '--name', title]);
    if (search.exitCode != 0) return false;

    final wids = (search.stdout as String)
        .trim()
        .split('\n')
        .where((s) => s.isNotEmpty)
        .toList();

    if (wids.isEmpty) return false;

    for (final wid in wids) {
      Process.runSync('xprop', [
        '-id', wid,
        '-f', '_NET_WM_BYPASS_COMPOSITOR', '32c',
        '-set', '_NET_WM_BYPASS_COMPOSITOR', '1',
      ]);
    }
    return true;
  } catch (_) {
    return false;
  }
}
