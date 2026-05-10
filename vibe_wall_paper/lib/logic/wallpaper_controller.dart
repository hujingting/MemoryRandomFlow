import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:palette_generator/palette_generator.dart';
import 'package:screenshot/screenshot.dart';
import 'package:image_gallery_saver_plus/image_gallery_saver_plus.dart';
import '../core/app_colors.dart';
import '../core/app_constants.dart';

/// Multi-image canvas arrangement (single-image mode ignores this).
enum MultiImageLayout {
  grid,
  scrapbook,
  stack,
}

class WallpaperController extends ChangeNotifier {
  final ImagePicker _picker = ImagePicker();
  final ScreenshotController screenshotController = ScreenshotController();

  List<XFile> _images = [];
  List<Color> _dominantColors = [];
  double _spacing = AppConstants.defaultSpacing;
  Color _bgColor = AppColors.background;
  bool _isSaving = false;
  bool _isSingleMode = false;
  int _selectedColorIndex = 0;
  bool _useNaturalBlend = true;
  Color _bottomBlendColor = Colors.black;
  
  double _singleScale = 1.0;
  double _singleOffsetX = 0.0;
  double _singleOffsetY = 0.0;
  
  int _currentTabIndex = 0;
  int _gridColumns = 3;
  bool _isPanelVisible = true;

  MultiImageLayout _multiImageLayout = MultiImageLayout.grid;
  /// 0–1, scales scrapbook offsets and rotations.
  double _scatterIntensity = 0.75;
  /// Base fan tilt for stack mode (degrees).
  double _stackAngleDegrees = 12;
  int _scrapbookSeed = 0xC0FFEE;
  int _stackSeed = 0xACE;

  /// User drag offset in preview pixels, keyed by image path.
  final Map<String, Offset> _scrapbookDragByPath = <String, Offset>{};
  final Map<String, Offset> _stackDragByPath = <String, Offset>{};
  final Map<String, double> _scrapbookScaleByPath = <String, double>{};
  final Map<String, double> _stackScaleByPath = <String, double>{};
  /// Seeded layout-only size jitter (multiply base tile × user pinch). Path-keyed.
  final Map<String, double> _scrapbookRandLayoutMulByPath = <String, double>{};
  final Map<String, double> _stackRandLayoutMulByPath = <String, double>{};

  /// Bumped when user picks photos; ignores stale palette work from a superseded pick.
  int _paletteGen = 0;

  // Getters
  List<XFile> get images => _images;
  List<Color> get dominantColors => _dominantColors;
  double get spacing => _spacing;
  Color get bgColor => _bgColor;
  bool get isSaving => _isSaving;
  bool get isSingleMode => _isSingleMode;
  int get selectedColorIndex => _selectedColorIndex;
  bool get useNaturalBlend => _useNaturalBlend;
  Color get bottomBlendColor => _bottomBlendColor;
  double get singleScale => _singleScale;
  double get singleOffsetX => _singleOffsetX;
  double get singleOffsetY => _singleOffsetY;
  int get currentTabIndex => _currentTabIndex;
  int get gridColumns => _gridColumns;
  bool get isPanelVisible => _isPanelVisible;
  MultiImageLayout get multiImageLayout => _multiImageLayout;
  double get scatterIntensity => _scatterIntensity;
  double get stackAngleDegrees => _stackAngleDegrees;

  // Setters with notifyListeners
  set isPanelVisible(bool value) {
    _isPanelVisible = value;
    notifyListeners();
  }

  set currentTabIndex(int index) {
    final int i = index.clamp(0, 1);
    if (_currentTabIndex == i) {
      _isPanelVisible = !_isPanelVisible;
    } else {
      _currentTabIndex = i;
      _isPanelVisible = true;
    }
    notifyListeners();
  }

  set gridColumns(int count) {
    _gridColumns = count.clamp(1, 6);
    notifyListeners();
  }

  set spacing(double value) {
    _spacing = value;
    notifyListeners();
  }

  set isSingleMode(bool value) {
    _isSingleMode = value;
    notifyListeners();
  }

  set useNaturalBlend(bool value) {
    _useNaturalBlend = value;
    notifyListeners();
  }

  set singleScale(double value) {
    _singleScale = value.clamp(AppConstants.minScale, AppConstants.maxScale);
    notifyListeners();
  }

  set singleOffsetX(double value) {
    _singleOffsetX = value.clamp(-1.0, 1.0);
    notifyListeners();
  }

  set singleOffsetY(double value) {
    _singleOffsetY = value.clamp(-1.0, 1.0);
    notifyListeners();
  }

  set multiImageLayout(MultiImageLayout value) {
    if (_multiImageLayout == value) return;
    _multiImageLayout = value;
    if (_images.length >= 2) {
      if (value == MultiImageLayout.scrapbook) {
        _regenerateScrapbookRandLayoutMul();
      } else if (value == MultiImageLayout.stack) {
        _regenerateStackRandLayoutMul();
      }
    }
    notifyListeners();
  }

  set scatterIntensity(double value) {
    _scatterIntensity = value.clamp(0.0, 1.0);
    notifyListeners();
  }

  set stackAngleDegrees(double value) {
    _stackAngleDegrees = value.clamp(0.0, 28.0);
    notifyListeners();
  }

  void selectColor(int index) {
    if (_dominantColors.isEmpty) return;
    final int hi = _dominantColors.length - 1;
    final int i = index < 0 ? 0 : (index > hi ? hi : index);
    _selectedColorIndex = i;
    _bgColor = _dominantColors[i];
    notifyListeners();
  }

  void _schedulePaletteAfterPick() {
    final int gen = ++_paletteGen;
    Future<void>.microtask(() async {
      if (gen != _paletteGen || _images.isEmpty) return;

      try {
        if (_isSingleMode) {
          await extractPaletteColors();
        } else {
          await extractPaletteColorsForMulti();
        }
      } catch (_) {
        /* keep prior colors / defaults */
      }
      if (gen == _paletteGen) {
        notifyListeners();
      }
    });
  }

  // Logic methods
  Future<void> pickSingleImage() async {
    try {
      final XFile? picked = await _picker.pickImage(source: ImageSource.gallery);
      if (picked == null) return;

      _images = [picked];
      _isSingleMode = true;
      _multiImageLayout = MultiImageLayout.grid;
      _pruneLayoutDragMaps();
      _singleScale = 1.0;
      _singleOffsetX = 0.0;
      _singleOffsetY = 0.0;
      _useNaturalBlend = true;

      notifyListeners();
      _schedulePaletteAfterPick();
    } catch (_) {
      rethrow;
    }
  }

  Future<void> pickMultiImages() async {
    try {
      final List<XFile> picked = await _picker.pickMultiImage();
      if (picked.isEmpty) return;

      final List<XFile> merged = [..._images, ...picked];
      if (merged.length > AppConstants.maxImages) {
        merged.removeRange(AppConstants.maxImages, merged.length);
      }

      _images = merged;
      _pruneLayoutDragMaps();
      _isSingleMode = merged.length == 1;
      if (!_isSingleMode) {
        _bumpLayoutSeeds();
      }
      if (_isSingleMode) {
        _multiImageLayout = MultiImageLayout.grid;
        _singleScale = 1.0;
        _singleOffsetX = 0.0;
        _singleOffsetY = 0.0;
        _useNaturalBlend = true;
      }

      notifyListeners();
      _schedulePaletteAfterPick();
    } catch (_) {
      rethrow;
    }
  }

  /// Palette + default background from first photo (multi/single previews use `_bgColor`).
  Future<void> extractPaletteColorsForMulti() async {
    if (_images.isEmpty) {
      _dominantColors = [];
      _bgColor = AppColors.background;
      _selectedColorIndex = 0;
      return;
    }

    try {
      final PaletteGenerator palette = await PaletteGenerator.fromImageProvider(
        FileImage(File(_images.first.path)),
        size: const Size(120, 120),
        maximumColorCount: 12,
      );

      final List<Color> colorList = palette.paletteColors
          .map((PaletteColor p) => p.color)
          .toSet()
          .toList();
      final Color fallback = palette.dominantColor?.color ?? AppColors.background;

      _dominantColors = colorList.isEmpty ? <Color>[fallback] : colorList;
      _selectedColorIndex = 0;
      _bgColor = _dominantColors.first;
    } catch (_) {
      _dominantColors = <Color>[AppColors.background];
      _selectedColorIndex = 0;
      _bgColor = AppColors.background;
    }
  }

  Future<void> extractPaletteColors() async {
    if (_images.isEmpty) {
      _dominantColors = [];
      _bgColor = AppColors.background;
      _selectedColorIndex = 0;
      return;
    }

    try {
      final PaletteGenerator palette = await PaletteGenerator.fromImageProvider(
        FileImage(File(_images.first.path)),
        size: const Size(120, 120),
        maximumColorCount: 12,
      );

      final List<Color> colorList = palette.paletteColors
          .map((PaletteColor p) => p.color)
          .toSet()
          .toList();
      final Color fallback = palette.dominantColor?.color ?? AppColors.background;

      _dominantColors = colorList.isEmpty ? [fallback] : colorList;
      _selectedColorIndex = 0;
      _bgColor = _dominantColors.first;
      _bottomBlendColor = _bgColor;

      _bottomBlendColor = await _extractBottomAreaColor(_images.first.path);
    } catch (_) {
      _dominantColors = [AppColors.background];
      _selectedColorIndex = 0;
      _bgColor = AppColors.background;
      _bottomBlendColor = AppColors.background;
    }
  }

  Future<Color> _extractBottomAreaColor(String path) async {
    Uint8List bytes;
    try {
      bytes = await File(path).readAsBytes();
    } catch (_) {
      return _bgColor;
    }

    ui.Codec? codec;
    ui.Image? image;
    try {
      codec = await ui.instantiateImageCodec(
        bytes,
        targetWidth: AppConstants.paletteThumbDecodePx,
      );
      final ui.FrameInfo frame = await codec.getNextFrame();
      image = frame.image;
      final ByteData? byteData = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
      if (byteData == null) return _bgColor;

      final Uint8List pixels = byteData.buffer.asUint8List();
      final int width = image.width;
      final int height = image.height;
      final int startY = (height * 0.85).floor();
      const int sampleStep = 2;
      int rTotal = 0, gTotal = 0, bTotal = 0, count = 0;

      for (int y = startY; y < height; y += sampleStep) {
        for (int x = 0; x < width; x += sampleStep) {
          final int offset = (y * width + x) * 4;
          if (pixels[offset + 3] == 0) continue;
          rTotal += pixels[offset];
          gTotal += pixels[offset + 1];
          bTotal += pixels[offset + 2];
          count++;
        }
      }

      return count == 0 ? _bgColor : Color.fromARGB(255, (rTotal / count).round(), (gTotal / count).round(), (bTotal / count).round());
    } catch (_) {
      return _bgColor;
    } finally {
      image?.dispose();
      codec?.dispose();
    }
  }

  void onReorder(int oldIndex, int newIndex) {
    final int targetIndex = newIndex > oldIndex ? newIndex - 1 : newIndex;
    final XFile item = _images.removeAt(oldIndex);
    _images.insert(targetIndex, item);
    notifyListeners();
  }

  void randomizeStackLayout() {
    _stackDragByPath.clear();
    _stackSeed = math.Random().nextInt(1 << 30) ^ DateTime.now().microsecondsSinceEpoch;
    _regenerateStackRandLayoutMul();
    notifyListeners();
  }

  void randomizeScrapbookLayout() {
    _scrapbookDragByPath.clear();
    _scrapbookSeed = math.Random().nextInt(1 << 30) ^ DateTime.now().microsecondsSinceEpoch;
    _regenerateScrapbookRandLayoutMul();
    notifyListeners();
  }

  Offset scrapbookDragOffset(String path) => _scrapbookDragByPath[path] ?? Offset.zero;

  Offset stackDragOffset(String path) => _stackDragByPath[path] ?? Offset.zero;

  void applyScrapbookDragDelta(String path, Offset delta) {
    final Offset cur = _scrapbookDragByPath[path] ?? Offset.zero;
    _scrapbookDragByPath[path] = cur + delta;
    notifyListeners();
  }

  void applyStackDragDelta(String path, Offset delta) {
    final Offset cur = _stackDragByPath[path] ?? Offset.zero;
    _stackDragByPath[path] = cur + delta;
    notifyListeners();
  }

  void _pruneLayoutDragMaps() {
    final Set<String> paths = _images.map((XFile e) => e.path).toSet();
    _scrapbookDragByPath.removeWhere((String k, _) => !paths.contains(k));
    _stackDragByPath.removeWhere((String k, _) => !paths.contains(k));
    _scrapbookScaleByPath.removeWhere((String k, _) => !paths.contains(k));
    _stackScaleByPath.removeWhere((String k, _) => !paths.contains(k));
    _scrapbookRandLayoutMulByPath.removeWhere((String k, _) => !paths.contains(k));
    _stackRandLayoutMulByPath.removeWhere((String k, _) => !paths.contains(k));
  }

  double scrapbookImageScale(String path) {
    final double? v = _scrapbookScaleByPath[path];
    final double s = v ?? 1.0;
    return s.clamp(AppConstants.minLayoutImageScale, AppConstants.maxLayoutImageScale);
  }

  double stackImageScale(String path) {
    final double? v = _stackScaleByPath[path];
    final double s = v ?? 1.0;
    return s.clamp(AppConstants.minLayoutImageScale, AppConstants.maxLayoutImageScale);
  }

  void setScrapbookImageScale(String path, double value) {
    _scrapbookScaleByPath[path] = value.clamp(AppConstants.minLayoutImageScale, AppConstants.maxLayoutImageScale);
    notifyListeners();
  }

  void setStackImageScale(String path, double value) {
    _stackScaleByPath[path] = value.clamp(AppConstants.minLayoutImageScale, AppConstants.maxLayoutImageScale);
    notifyListeners();
  }

  void _bumpLayoutSeeds() {
    final math.Random r = math.Random();
    _scrapbookSeed = r.nextInt(1 << 30) ^ _images.length * 9973;
    _stackSeed = r.nextInt(1 << 30) ^ _images.length * 7919;
    _regenerateScrapbookRandLayoutMul();
    _regenerateStackRandLayoutMul();
  }

  /// Upper bound used for scrapbook hit-reach (`halfReach`).
  static double scrapbookRandLayoutMulUpperBoundForCount(int rawN) {
    final int n = rawN.clamp(1, AppConstants.maxImages);
    if (n <= 2) return 1.42;
    if (n <= 5) return 1.28;
    if (n <= 9) return 1.14;
    return 1.04;
  }

  static double stackRandLayoutMulUpperBoundForCount(int rawN) {
    final int n = rawN.clamp(1, AppConstants.maxImages);
    if (n <= 2) return 1.38;
    if (n <= 5) return 1.22;
    return 1.06;
  }

  double scrapbookRandLayoutMultiplier(String path) => _scrapbookRandLayoutMulByPath[path] ?? 1.0;

  double stackRandLayoutMultiplier(String path) => _stackRandLayoutMulByPath[path] ?? 1.0;

  void _regenerateScrapbookRandLayoutMul() {
    _scrapbookRandLayoutMulByPath.clear();
    if (_images.isEmpty) return;
    final int n = _images.length;
    late double lo;
    late double hi;
    if (n <= 2) {
      lo = 0.82;
      hi = 1.42;
    } else if (n <= 5) {
      lo = 0.70;
      hi = 1.26;
    } else if (n <= 9) {
      lo = 0.58;
      hi = 1.12;
    } else {
      lo = 0.50;
      hi = 1.02;
    }
    final math.Random r = math.Random(_scrapbookSeed ^ 0xC0FF601D);
    for (final XFile x in _images) {
      _scrapbookRandLayoutMulByPath[x.path] = lo + r.nextDouble() * (hi - lo);
    }
  }

  void _regenerateStackRandLayoutMul() {
    _stackRandLayoutMulByPath.clear();
    if (_images.isEmpty) return;
    final int n = _images.length;
    late double lo;
    late double hi;
    if (n <= 2) {
      lo = 0.84;
      hi = 1.36;
    } else if (n <= 5) {
      lo = 0.72;
      hi = 1.18;
    } else {
      lo = 0.62;
      hi = 1.06;
    }
    final math.Random r = math.Random(_stackSeed ^ 0x5CA1E);
    for (final XFile x in _images) {
      _stackRandLayoutMulByPath[x.path] = lo + r.nextDouble() * (hi - lo);
    }
  }

  /// Normalized offsets in [-1,1]²; fills the rectangle (Latin-style X/Y strata + edge stretch).
  List<Offset> scrapbookNormalizedOffsets() {
    final int n = _images.length;
    final math.Random r = math.Random(_scrapbookSeed);
    if (n == 0) return <Offset>[];
    if (n == 1) {
      return <Offset>[
        Offset((r.nextDouble() * 2 - 1) * 0.12, (r.nextDouble() * 2 - 1) * 0.12),
      ];
    }
    return _scrapbookOffsetsLatinFill(n, r);
  }

  /// One sample per stripe on X and independently on Y, shuffled → good 2-D coverage vs grid diagonal clumping.
  List<double> _scrapbookStratifiedAxisSamples(int n, math.Random r) {
    final List<double> xs = List<double>.generate(n, (int i) {
      final double a = -1.0 + 2.0 * i / n;
      final double b = -1.0 + 2.0 * (i + 1) / n;
      return a + r.nextDouble() * (b - a);
    });
    xs.shuffle(r);
    return xs;
  }

  /// Pull toward ±1 inside [-1,1] so rectangles use corners instead of drifting to the centre band.
  static double _expandTowardEdges01(double x) {
    final double ax = x.abs().clamp(0.0, 1.0);
    if (ax < 1e-9) return x;
    return x.sign * math.pow(ax, 0.58).toDouble().clamp(0.0, 1.0);
  }

  List<Offset> _scrapbookOffsetsLatinFill(int n, math.Random r) {
    final List<double> xs = _scrapbookStratifiedAxisSamples(n, r);
    final List<double> ys = _scrapbookStratifiedAxisSamples(n, math.Random(_scrapbookSeed ^ 0xBADC0FFE));
    final double jitter = (0.078 / math.sqrt(n)).clamp(0.018, 0.074);
    final double chaos = _scatterIntensity * 0.11;
    return List<Offset>.generate(n, (int i) {
      double dx = xs[i] + (r.nextDouble() * 2 - 1) * jitter + (r.nextDouble() * 2 - 1) * chaos;
      double dy = ys[i] + (r.nextDouble() * 2 - 1) * jitter + (r.nextDouble() * 2 - 1) * chaos;
      dx = _expandTowardEdges01(dx.clamp(-1.0, 1.0));
      dy = _expandTowardEdges01(dy.clamp(-1.0, 1.0));
      return Offset(dx.clamp(-1.0, 1.0), dy.clamp(-1.0, 1.0));
    });
  }

  /// Fraction of min(preview side) for base tile length (before pinch). Smaller when many photos.
  double scrapbookLayoutBaseTileFraction(int imageCount) {
    final int n = imageCount.clamp(1, AppConstants.maxImages);
    if (n <= 1) return 0.38;
    if (n == 2) return 0.31;
    if (n == 3) return 0.278;
    if (n <= 4) return 0.248;
    if (n <= 6) return 0.218;
    if (n <= 9) return 0.190;
    if (n <= 12) return 0.172;
    return (0.70 / math.sqrt(n)).clamp(0.126, 0.168);
  }

  /// Scales rotation with count so many thumbnails stay readable.
  double scrapbookLayoutRotationScale(int imageCount) {
    final int n = imageCount.clamp(1, AppConstants.maxImages);
    if (n <= 2) return 0.96;
    if (n <= 4) return 0.82;
    if (n <= 8) return 0.68;
    if (n <= 12) return 0.56;
    return 0.48;
  }

  /// Normalized rotation bias [-1, 1] per index.
  List<double> scrapbookNormalizedRotations() {
    final math.Random r = math.Random(_scrapbookSeed ^ 0x5EED);
    return List<double>.generate(_images.length, (_) => r.nextDouble() * 2 - 1);
  }

  /// Extra rotation (radians) per stack slot from back to front order.
  List<double> stackExtraRotationsRad(int count) {
    final math.Random r = math.Random(_stackSeed);
    return List<double>.generate(
      count,
      (_) => (r.nextDouble() * 2 - 1) * 0.12,
    );
  }

  /// Horizontal jitter for stack (normalized -1..1 scaled in UI).
  List<double> stackHorizontalJitter(int count) {
    final math.Random r = math.Random(_stackSeed ^ 0x51DE);
    return List<double>.generate(count, (_) => r.nextDouble() * 2 - 1);
  }

  /// Draw order for scrapbook (back → front).
  List<int> scrapbookPaintOrder() {
    final List<int> indices = List<int>.generate(_images.length, (int i) => i);
    indices.shuffle(math.Random(_scrapbookSeed ^ 0xCABC));
    return indices;
  }

  Future<bool> saveToGallery() async {
    if (_images.isEmpty || _isSaving) return false;

    _isSaving = true;
    notifyListeners();

    try {
      final Uint8List? bytes = await screenshotController.capture(pixelRatio: 3);
      if (bytes == null || bytes.isEmpty) {
        _isSaving = false;
        notifyListeners();
        return false;
      }

      final dynamic result = await ImageGallerySaverPlus.saveImage(
        bytes,
        quality: 100,
        name: 'music_wallpaper_${DateTime.now().millisecondsSinceEpoch}',
      );

      _isSaving = false;
      notifyListeners();
      return _isSaveSuccess(result);
    } catch (_) {
      _isSaving = false;
      notifyListeners();
      return false;
    }
  }

  bool _isSaveSuccess(dynamic result) {
    if (result is bool) return result;
    if (result is Map) {
      final dynamic isSuccess = result['isSuccess'];
      if (isSuccess is bool) return isSuccess;
      final dynamic filePath = result['filePath'];
      return filePath != null && filePath.toString().isNotEmpty;
    }
    return false;
  }

  void clearAll() {
    _images = [];
    _dominantColors = [];
    _spacing = AppConstants.defaultSpacing;
    _bgColor = AppColors.background;
    _isSingleMode = false;
    _selectedColorIndex = 0;
    _useNaturalBlend = true;
    _bottomBlendColor = AppColors.background;
    _singleScale = 1.0;
    _singleOffsetX = 0.0;
    _singleOffsetY = 0.0;
    _multiImageLayout = MultiImageLayout.grid;
    _scatterIntensity = 0.75;
    _stackAngleDegrees = 12;
    _scrapbookDragByPath.clear();
    _stackDragByPath.clear();
    _scrapbookScaleByPath.clear();
    _stackScaleByPath.clear();
    _scrapbookRandLayoutMulByPath.clear();
    _stackRandLayoutMulByPath.clear();
    _bumpLayoutSeeds();
    notifyListeners();
  }

  /// Updates scale and normalized offsets in one [notifyListeners] (for smooth gestures).
  void applySingleGestureTransform({
    required double scale,
    required double offsetX,
    required double offsetY,
  }) {
    _singleScale = scale.clamp(AppConstants.minScale, AppConstants.maxScale);
    _singleOffsetX = offsetX.clamp(-1.0, 1.0);
    _singleOffsetY = offsetY.clamp(-1.0, 1.0);
    notifyListeners();
  }
}
