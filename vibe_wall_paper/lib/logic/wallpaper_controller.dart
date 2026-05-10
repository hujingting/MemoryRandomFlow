import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:palette_generator/palette_generator.dart';
import 'package:screenshot/screenshot.dart';
import 'package:image_gallery_saver_plus/image_gallery_saver_plus.dart';
import '../core/app_colors.dart';
import '../core/app_constants.dart';

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

  // Setters with notifyListeners
  set isPanelVisible(bool value) {
    _isPanelVisible = value;
    notifyListeners();
  }

  set currentTabIndex(int index) {
    if (_currentTabIndex == index) {
      _isPanelVisible = !_isPanelVisible;
    } else {
      _currentTabIndex = index;
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

  void selectColor(int index) {
    _selectedColorIndex = index;
    _bgColor = _dominantColors[index];
    notifyListeners();
  }

  // Logic methods
  Future<void> pickSingleImage() async {
    try {
      final XFile? picked = await _picker.pickImage(source: ImageSource.gallery);
      if (picked == null) return;

      _images = [picked];
      _isSingleMode = true;
      _singleScale = 1.0;
      _singleOffsetX = 0.0;
      _singleOffsetY = 0.0;
      _useNaturalBlend = true;
      
      await extractPaletteColors();
      notifyListeners();
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
      _isSingleMode = merged.length == 1;
      if (_isSingleMode) {
        _singleScale = 1.0;
        _singleOffsetX = 0.0;
        _singleOffsetY = 0.0;
        _useNaturalBlend = true;
        await extractPaletteColors();
      } else {
        _dominantColors = [];
        _selectedColorIndex = 0;
        await extractBackgroundColorForGrid();
      }
      notifyListeners();
    } catch (_) {
      rethrow;
    }
  }

  Future<void> extractBackgroundColorForGrid() async {
    if (_images.isEmpty) {
      _bgColor = AppColors.background;
      return;
    }

    try {
      final PaletteGenerator palette = await PaletteGenerator.fromImageProvider(
        FileImage(File(_images.first.path)),
        size: const Size(100, 100),
      );
      _bgColor = palette.dominantColor?.color ?? AppColors.background;
    } catch (_) {
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
    final Uint8List bytes = await File(path).readAsBytes();
    final ui.Image image = await decodeImageFromList(bytes);
    final ByteData? byteData = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
    if (byteData == null) return _bgColor;

    final Uint8List pixels = byteData.buffer.asUint8List();
    final int width = image.width;
    final int height = image.height;
    final int startY = (height * 0.85).floor();
    const int sampleStep = 4;
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
  }

  void onReorder(int oldIndex, int newIndex) {
    final int targetIndex = newIndex > oldIndex ? newIndex - 1 : newIndex;
    final XFile item = _images.removeAt(oldIndex);
    _images.insert(targetIndex, item);
    notifyListeners();
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
