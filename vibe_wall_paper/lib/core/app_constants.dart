class AppConstants {
  static const int maxImages = 16;
  static const double minSpacing = 0;
  static const double maxSpacing = 40;
  static const double minScale = 0.5;
  static const double maxScale = 3.0;
  static const double defaultSpacing = 8.0;
  /// Per-image scale in scrapbook / stack preview (pinch).
  static const double minLayoutImageScale = 0.32;
  static const double maxLayoutImageScale = 2.85;

  /// Decoded image pixels for preview thumbnails (width or height hint for [Image.cacheWidth/cacheHeight]).
  static const int previewThumbDecodePx = 960;
  /// Decoded longest side for single-image fullscreen preview.
  static const int previewSingleDecodePx = 1536;

  /// Thumbnail longest side used when extracting bottom-strip color so we skip full-resolution decode.
  static const int paletteThumbDecodePx = 128;
}
