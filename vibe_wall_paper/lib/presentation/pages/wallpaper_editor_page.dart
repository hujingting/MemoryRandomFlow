import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:reorderable_grid_view/reorderable_grid_view.dart';
import 'package:screenshot/screenshot.dart';
import '../../core/app_colors.dart';
import '../../core/app_constants.dart';
import '../../logic/wallpaper_controller.dart';
import '../widgets/settings_components.dart';

class WallpaperEditorPage extends StatefulWidget {
  const WallpaperEditorPage({super.key});

  @override
  State<WallpaperEditorPage> createState() => _WallpaperEditorPageState();
}

class _WallpaperEditorPageState extends State<WallpaperEditorPage> {
  late final WallpaperController _controller;

  /// Persists across rebuilds; locals inside [LayoutBuilder] were reset every frame.
  double _gestureBaseScale = 1.0;
  double _gestureBaseOffsetX = 0.0;
  double _gestureBaseOffsetY = 0.0;
  Offset _gestureFocalPoint = Offset.zero;

  String? _scrapbookPinchPath;
  double? _scrapbookPinchBaseScale;
  String? _stackPinchPath;
  double? _stackPinchBaseScale;

  @override
  void initState() {
    super.initState();
    _controller = WallpaperController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  /// Physical pixels: limits bitmap decode for grid / scrapbook / stack previews.
  int _thumbDecodePx(BuildContext context) {
    final double dpr = MediaQuery.devicePixelRatioOf(context);
    final double logical = MediaQuery.sizeOf(context).shortestSide * 0.52;
    return (logical * dpr).round().clamp(220, AppConstants.previewThumbDecodePx);
  }

  int _singlePreviewDecodePx(BuildContext context) {
    final double dpr = MediaQuery.devicePixelRatioOf(context);
    return (MediaQuery.sizeOf(context).longestSide * dpr).round().clamp(600, AppConstants.previewSingleDecodePx);
  }

  int _stackCardDecodePx(BuildContext context, double cw, double ch) {
    final double dpr = MediaQuery.devicePixelRatioOf(context);
    return (math.max(cw, ch) * dpr).round().clamp(180, AppConstants.previewThumbDecodePx);
  }

  void _scrapbookScaleGestureStart(String path) {
    _scrapbookPinchPath = path;
    _scrapbookPinchBaseScale = _controller.scrapbookImageScale(path);
  }

  void _scrapbookScaleGestureUpdate(String path, ScaleUpdateDetails details) {
    if (_scrapbookPinchPath != path || _scrapbookPinchBaseScale == null) return;
    final bool pinch = details.pointerCount >= 2 || (details.scale - 1.0).abs() > 0.02;
    if (pinch) {
      _controller.setScrapbookImageScale(path, _scrapbookPinchBaseScale! * details.scale);
    } else {
      _controller.applyScrapbookDragDelta(path, details.focalPointDelta);
    }
  }

  void _scrapbookScaleGestureEnd(String path) {
    if (_scrapbookPinchPath == path) {
      _scrapbookPinchPath = null;
      _scrapbookPinchBaseScale = null;
    }
  }

  void _stackScaleGestureStart(String path) {
    _stackPinchPath = path;
    _stackPinchBaseScale = _controller.stackImageScale(path);
  }

  void _stackScaleGestureUpdate(String path, ScaleUpdateDetails details) {
    if (_stackPinchPath != path || _stackPinchBaseScale == null) return;
    final bool pinch = details.pointerCount >= 2 || (details.scale - 1.0).abs() > 0.02;
    if (pinch) {
      _controller.setStackImageScale(path, _stackPinchBaseScale! * details.scale);
    } else {
      _controller.applyStackDragDelta(path, details.focalPointDelta);
    }
  }

  void _stackScaleGestureEnd(String path) {
    if (_stackPinchPath == path) {
      _stackPinchPath = null;
      _stackPinchBaseScale = null;
    }
  }

  Future<void> _handleSave() async {
    final success = await _controller.saveToGallery();
    if (success) {
      _showSnackBar('已保存到相册');
    } else {
      _showSnackBar('保存失败，请检查权限或重试');
    }
  }

  Future<void> _showAddImageOptions() async {
    final String? action = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: AppColors.surfaceContainerHigh,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (BuildContext context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const SizedBox(height: 8),
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.outlineVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: const Icon(Icons.photo, color: AppColors.primary),
                title: const Text('添加一张', style: TextStyle(color: AppColors.onSurface)),
                onTap: () => Navigator.of(context).pop('single'),
              ),
              ListTile(
                leading: const Icon(Icons.collections, color: AppColors.primary),
                title: const Text('添加多张', style: TextStyle(color: AppColors.onSurface)),
                onTap: () => Navigator.of(context).pop('multi'),
              ),
              const SizedBox(height: 16),
            ],
          ),
        );
      },
    );

    if (!mounted || action == null) return;
    try {
      if (action == 'single') {
        await _controller.pickSingleImage();
      } else {
        await _controller.pickMultiImages();
      }
    } catch (_) {
      _showSnackBar('操作失败，请稍后重试');
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _controller,
      builder: (context, _) {
        final Size screenSize = MediaQuery.of(context).size;
        final double aspectRatio = screenSize.width / screenSize.height;

        return Scaffold(
          backgroundColor: AppColors.background,
          appBar: AppBar(
            leading: IconButton(
              icon: const Icon(Icons.grid_view_rounded, color: AppColors.onSurface),
              onPressed: () {},
            ),
            title: const Text('Studio'),
            actions: [
              TextButton(
                onPressed: _controller.images.isEmpty ? null : _controller.clearAll,
                child: const Text('Clear', style: TextStyle(color: AppColors.onSurfaceVariant)),
              ),
              const SizedBox(width: 8),
              Padding(
                padding: const EdgeInsets.only(right: 16),
                child: FilledButton(
                  onPressed: _controller.isSaving ? null : _handleSave,
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.accent,
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                  ),
                  child: Text(_controller.isSaving ? 'Saving...' : 'Save'),
                ),
              ),
            ],
          ),
          body: Column(
            children: [
              Expanded(
                flex: (_controller.images.isNotEmpty && _controller.isPanelVisible) ? 2 : 1,
                child: Center(
                  child: _controller.images.isEmpty ? _buildEmptyState() : _buildPreview(context, aspectRatio),
                ),
              ),
              if (_controller.images.isNotEmpty && _controller.isPanelVisible)
                Expanded(
                  flex: 1,
                  child: _buildControlPanelExpanded(),
                ),
              if (_controller.images.isNotEmpty && !_controller.isPanelVisible) _buildControlPanelCollapsed(),
              _buildBottomNav(),
            ],
          ),
        );
      },
    );
  }

  Widget _buildEmptyState() {
    return Container(
      margin: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        border: Border.all(
          color: AppColors.outlineVariant.withValues(alpha: 0.5),
          width: 1,
        ),
        borderRadius: BorderRadius.circular(32),
      ),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: const BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.add_photo_alternate_outlined, size: 40, color: AppColors.primary),
            ),
            const SizedBox(height: 24),
            const Text(
              '添加封面图片后即可预览\n壁纸',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: AppColors.onSurface,
                fontSize: 20,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Start by selecting one or\nmore album covers to\ngenerate your\nprofessional aesthetic\nwallpaper.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: AppColors.onSurfaceVariant,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 40),
            FilledButton.icon(
              onPressed: _showAddImageOptions,
              icon: const Icon(Icons.add),
              label: const Text('Add Images'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPreview(BuildContext context, double aspectRatio) {
    return Container(
      margin: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.45),
            blurRadius: 32,
            spreadRadius: -8,
          ),
        ],
      ),
      child: Screenshot(
        controller: _controller.screenshotController,
        child: AspectRatio(
          aspectRatio: aspectRatio,
          child: Container(
            decoration: _buildCanvasDecoration(),
            padding: EdgeInsets.all(
              _controller.isSingleMode
                  ? 0
                  : (_controller.multiImageLayout == MultiImageLayout.grid
                      ? _controller.spacing
                      : 0),
            ),
            child: _controller.isSingleMode ? _buildSingleImagePreview(context) : _buildMultiImagePreview(context),
          ),
        ),
      ),
    );
  }

  BoxDecoration _buildCanvasDecoration() {
    if (_controller.isSingleMode && _controller.useNaturalBlend) {
      return BoxDecoration(color: _controller.bottomBlendColor);
    }
    return BoxDecoration(color: _controller.bgColor);
  }

  Widget _buildMultiImagePreview(BuildContext context) {
    switch (_controller.multiImageLayout) {
      case MultiImageLayout.grid:
        return _buildGridPreview(context);
      case MultiImageLayout.scrapbook:
        return _buildScrapbookPreview(context);
      case MultiImageLayout.stack:
        return _buildStackPreview(context);
    }
  }

  Widget _buildGridPreview(BuildContext context) {
    final int decodePx = _thumbDecodePx(context);
    return ReorderableGridView.count(
      crossAxisCount: _controller.gridColumns,
      crossAxisSpacing: _controller.spacing,
      mainAxisSpacing: _controller.spacing,
      onReorder: _controller.onReorder,
      children: _controller.images.map((XFile file) {
        return ClipRRect(
          key: ValueKey<String>(file.path),
          borderRadius: BorderRadius.circular(8),
          child: AspectRatio(
            aspectRatio: 1,
            child: Image.file(
              File(file.path),
              fit: BoxFit.cover,
              cacheWidth: decodePx,
              cacheHeight: decodePx,
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildScrapbookPreview(BuildContext context) {
    final List<XFile> files = _controller.images;
    if (files.isEmpty) return const SizedBox.shrink();

    final List<Offset> offsets = _controller.scrapbookNormalizedOffsets();
    final List<double> rotations = _controller.scrapbookNormalizedRotations();
    final List<int> paintOrder = _controller.scrapbookPaintOrder();
    final double scatter = _controller.scatterIntensity;
    final int decodePx = _thumbDecodePx(context);

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final double w = constraints.maxWidth;
        final double h = constraints.maxHeight;
        final double base = math.min(w, h);
        final int n = files.length;
        final double frac = _controller.scrapbookLayoutBaseTileFraction(n);
        final double card = (base * frac * 0.92).clamp(base * 0.12, base * 0.40);
        final double photo = card;
        final double rotScale = _controller.scrapbookLayoutRotationScale(n);
        const double edgePad = 1.0;
        // Worst-case half-width: baseline × max random layout mul × pinch.
        final double maxLm = WallpaperController.scrapbookRandLayoutMulUpperBoundForCount(n);
        final double maxBoxHalf = photo * maxLm * AppConstants.maxLayoutImageScale * 0.5;
        final double halfReachX = math.max(0.0, w * 0.5 - maxBoxHalf - edgePad);
        final double halfReachY = math.max(0.0, h * 0.5 - maxBoxHalf - edgePad);
        // Layout already spreads in [-1,1]²; slider mainly tilts/rotates — keep position near full span.
        final double reach = scatter.clamp(0.0, 1.0);
        final double reachAmp = 0.90 + 0.10 * reach;

        return Stack(
          clipBehavior: Clip.none,
          children: paintOrder.map((int i) {
            final XFile file = files[i];
            final Offset o = offsets[i];
            final double rot = rotations[i] * 0.78 * reach * rotScale;
            final double dx = o.dx * halfReachX * reachAmp;
            final double dy = o.dy * halfReachY * reachAmp;
            final Offset drag = _controller.scrapbookDragOffset(file.path);
            final double imgScale = _controller.scrapbookImageScale(file.path);
            final double layMul = _controller.scrapbookRandLayoutMultiplier(file.path);
            final double layPhoto = photo * layMul;
            final double box = layPhoto * imgScale;

            return Positioned(
              key: ValueKey<String>('scrap-${file.path}-$i'),
              left: w / 2 + dx - box / 2 + drag.dx,
              top: h / 2 + dy - box / 2 + drag.dy,
              width: box,
              height: box,
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onScaleStart: (_) => _scrapbookScaleGestureStart(file.path),
                onScaleUpdate: (ScaleUpdateDetails d) => _scrapbookScaleGestureUpdate(file.path, d),
                onScaleEnd: (_) => _scrapbookScaleGestureEnd(file.path),
                child: Center(
                  child: Transform.scale(
                    scale: imgScale,
                    alignment: Alignment.center,
                    child: SizedBox(
                      width: layPhoto,
                      height: layPhoto,
                      child: Transform.rotate(
                        angle: rot,
                        child: _scrapbookTile(File(file.path), layPhoto, decodePx),
                      ),
                    ),
                  ),
                ),
              ),
            );
          }).toList(),
        );
      },
    );
  }

  Widget _scrapbookTile(File file, double size, int decodePx) {
    final List<BoxShadow> shadows = <BoxShadow>[
      BoxShadow(
        color: Colors.black.withValues(alpha: 0.55),
        blurRadius: 18,
        offset: const Offset(6, 10),
        spreadRadius: -2,
      ),
      BoxShadow(
        color: Colors.black.withValues(alpha: 0.25),
        blurRadius: 6,
        offset: const Offset(0, 2),
      ),
    ];

    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        boxShadow: shadows,
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: SizedBox(
          width: size,
          height: size,
          child: Image.file(
            file,
            fit: BoxFit.cover,
            cacheWidth: decodePx,
            cacheHeight: decodePx,
          ),
        ),
      ),
    );
  }

  Widget _buildStackPreview(BuildContext context) {
    final List<XFile> files = _controller.images;
    if (files.isEmpty) return const SizedBox.shrink();

    final int n = files.length;
    final List<double> extraRot = _controller.stackExtraRotationsRad(n);
    final List<double> jitterX = _controller.stackHorizontalJitter(n);
    final double angleDeg = _controller.stackAngleDegrees;
    final double angleRad = angleDeg * math.pi / 180;

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final double side =
            math.min(constraints.maxWidth * 0.78, constraints.maxHeight * 0.62) * (1 + 0.04 * math.min(n, 9) / 9);

        return Stack(
          alignment: Alignment.center,
          clipBehavior: Clip.none,
          children: List<Widget>.generate(n, (int i) {
            final double depth = n <= 1 ? 0 : i / (n - 1);
            final double fan = (i - (n - 1) / 2) * angleRad * 0.11;
            final double rot = fan + extraRot[i] * (0.6 + angleRad * 0.08);
            final double scale = 1.0 - depth * 0.065;
            final double ox = jitterX[i] * 14 * (1 + angleDeg / 14);
            final double oy = i * 9.0;
            final String path = files[i].path;
            final Offset stackDrag = _controller.stackDragOffset(path);
            final double imgScale = _controller.stackImageScale(path);
            final double lm = _controller.stackRandLayoutMultiplier(path);
            final double cw = side * scale * lm * imgScale;
            final double ch = side * scale * lm * 1.12 * imgScale;

            Widget face = Transform(
              transform: Matrix4.identity()
                ..setEntry(3, 2, 0.00115)
                ..rotateY(rot * 1.35)
                ..rotateX(-depth * 0.08),
              alignment: Alignment.center,
              child: Transform.rotate(
                angle: rot,
                child: Container(
                  width: cw,
                  height: ch,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(20),
                    boxShadow: <BoxShadow>[
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.45),
                        blurRadius: 22,
                        offset: Offset(0, 10 + depth * 8),
                      ),
                    ],
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(20),
                    child: Image.file(
                      File(files[i].path),
                      fit: BoxFit.cover,
                      width: cw,
                      height: ch,
                      cacheWidth: _stackCardDecodePx(context, cw, ch),
                    ),
                  ),
                ),
              ),
            );

            return KeyedSubtree(
              key: ValueKey<String>('stack-$path-$i'),
              child: Transform.translate(
                offset: Offset(ox, oy) + stackDrag,
                child: GestureDetector(
                  behavior: HitTestBehavior.deferToChild,
                  onScaleStart: (_) => _stackScaleGestureStart(path),
                  onScaleUpdate: (ScaleUpdateDetails d) => _stackScaleGestureUpdate(path, d),
                  onScaleEnd: (_) => _stackScaleGestureEnd(path),
                  child: face,
                ),
              ),
            );
          }),
        );
      },
    );
  }

  Widget _buildSingleImagePreview(BuildContext context) {
    final XFile file = _controller.images.first;
    final int decodePx = _singlePreviewDecodePx(context);
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final double maxX = constraints.maxWidth / 2;
        final double maxY = constraints.maxHeight / 2;

        return ClipRect(
          child: GestureDetector(
            onScaleStart: (ScaleStartDetails details) {
              _gestureBaseScale = _controller.singleScale;
              _gestureBaseOffsetX = _controller.singleOffsetX;
              _gestureBaseOffsetY = _controller.singleOffsetY;
              _gestureFocalPoint = details.focalPoint;
            },
            onScaleUpdate: (ScaleUpdateDetails details) {
              final double dx =
                  (details.focalPoint.dx - _gestureFocalPoint.dx) / maxX;
              final double dy =
                  (details.focalPoint.dy - _gestureFocalPoint.dy) / maxY;
              _controller.applySingleGestureTransform(
                scale: _gestureBaseScale * details.scale,
                offsetX: _gestureBaseOffsetX + dx,
                offsetY: _gestureBaseOffsetY + dy,
              );
            },
            child: Transform.translate(
              offset: Offset(
                _controller.singleOffsetX * maxX,
                _controller.singleOffsetY * maxY,
              ),
              child: Transform.scale(
                scale: _controller.singleScale,
                alignment: Alignment.center,
                child: SizedBox(
                  width: constraints.maxWidth,
                  height: constraints.maxHeight,
                  child: Image.file(
                    File(file.path),
                    fit: BoxFit.fitWidth,
                    alignment: Alignment.center,
                    cacheWidth: decodePx,
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildControlPanelExpanded() {
    return GestureDetector(
      onVerticalDragUpdate: (DragUpdateDetails details) {
        if (details.primaryDelta! > 10) {
          _controller.isPanelVisible = false;
        }
      },
      child: ClipRRect(
        borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: AppColors.surfaceContainer.withValues(alpha: 0.95),
            border: const Border(top: BorderSide(color: Colors.white10)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
                child: Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 12),
                    decoration: BoxDecoration(
                      color: Colors.white10,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
              ),
              Expanded(
                child: SingleChildScrollView(
                  physics: const BouncingScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (_controller.currentTabIndex == 0) _buildLayoutTabSettings(),
                      if (_controller.currentTabIndex == 1) _buildThemeTabSettings(),
                      const SizedBox(height: 8),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildControlPanelCollapsed() {
    return GestureDetector(
      onTap: () => _controller.isPanelVisible = true,
      onVerticalDragUpdate: (DragUpdateDetails details) {
        if (details.primaryDelta! < -10) {
          _controller.isPanelVisible = true;
        }
      },
      child: Container(
        width: double.infinity,
        height: 24,
        color: Colors.transparent,
        child: Center(
          child: Container(
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: Colors.white10,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
      ),
    );
  }

  /// Single/Grid mode + thumbnails layout (GRID + former LAYOUT tab).
  Widget _buildLayoutTabSettings() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildLayoutSettings(),
        if (!_controller.isSingleMode) ...[
          const SizedBox(height: 24),
          const Divider(height: 1, color: Colors.white12),
          const SizedBox(height: 20),
          _buildMultiImageLayoutControls(),
        ],
      ],
    );
  }

  /// Palette + overlay (merged THEME tab).
  Widget _buildThemeTabSettings() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildThemeSettings(),
        const SizedBox(height: 28),
        _buildOverlaySettings(),
      ],
    );
  }

  /// Multi-image: grid / scrapbook / stack controls (former GRID panel).
  Widget _buildMultiImageLayoutControls() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          '布局样式',
          style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurfaceVariant, letterSpacing: 1.0),
        ),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Expanded(
                child: ToggleButton(
                  label: '网格',
                  active: _controller.multiImageLayout == MultiImageLayout.grid,
                  onTap: () => _controller.multiImageLayout = MultiImageLayout.grid,
                ),
              ),
              Expanded(
                child: ToggleButton(
                  label: '随心贴',
                  active: _controller.multiImageLayout == MultiImageLayout.scrapbook,
                  onTap: () => _controller.multiImageLayout = MultiImageLayout.scrapbook,
                ),
              ),
              Expanded(
                child: ToggleButton(
                  label: '层叠',
                  active: _controller.multiImageLayout == MultiImageLayout.stack,
                  onTap: () => _controller.multiImageLayout = MultiImageLayout.stack,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        if (_controller.multiImageLayout == MultiImageLayout.grid) ...[
          const Text('长按封面可调整顺序', style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 12)),
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: SettingsCard(
                  title: 'COLUMNS',
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('${_controller.gridColumns}', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
                      Container(
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHigh,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(
                          children: [
                            IconButton(
                              icon: const Icon(Icons.remove, size: 20),
                              onPressed: _controller.gridColumns > 1 ? () => _controller.gridColumns-- : null,
                            ),
                            Container(width: 1, height: 20, color: Colors.white10),
                            IconButton(
                              icon: const Icon(Icons.add, size: 20, color: AppColors.accent),
                              onPressed: _controller.gridColumns < 6 ? () => _controller.gridColumns++ : null,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: SettingsCard(
                  title: 'SPACING',
                  trailing: '${_controller.spacing.toInt()}px',
                  child: Slider(
                    min: AppConstants.minSpacing,
                    max: AppConstants.maxSpacing,
                    value: _controller.spacing,
                    onChanged: (v) => _controller.spacing = v,
                  ),
                ),
              ),
            ],
          ),
        ],
        if (_controller.multiImageLayout == MultiImageLayout.scrapbook) ...[
          SettingsCard(
            title: '离散强度',
            trailing: '${(_controller.scatterIntensity * 100).round()}%',
            child: Slider(
              value: _controller.scatterIntensity,
              min: 0,
              max: 1,
              onChanged: (double v) => _controller.scatterIntensity = v,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            '调节散落与倾斜；单指拖动位置，双指捏合单独缩放每张图。',
            style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 12, height: 1.35),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: _controller.randomizeScrapbookLayout,
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.accent,
                foregroundColor: AppColors.onPrimary,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              ),
              icon: const Icon(Icons.auto_awesome_mosaic_outlined),
              label: const Text('重新散落', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
            ),
          ),
        ],
        if (_controller.multiImageLayout == MultiImageLayout.stack) ...[
          SettingsCard(
            title: '堆叠角度',
            trailing: '${_controller.stackAngleDegrees.round()}°',
            child: Slider(
              value: _controller.stackAngleDegrees,
              min: 0,
              max: 28,
              onChanged: (double v) => _controller.stackAngleDegrees = v,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            '单指拖动位置，双指捏合调整该张大小。',
            style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 12, height: 1.35),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: _controller.randomizeStackLayout,
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.accent,
                foregroundColor: AppColors.onPrimary,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              ),
              icon: const Icon(Icons.shuffle_rounded),
              label: const Text('随机排列', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildThemeSettings() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('PALETTE', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurfaceVariant, letterSpacing: 1.2)),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: SizedBox(
                height: 48,
                child: _controller.dominantColors.isEmpty
                    ? const Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          '载入调色…',
                          style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 13),
                        ),
                      )
                    : ListView.builder(
                        scrollDirection: Axis.horizontal,
                        itemCount: _controller.dominantColors.length,
                        itemBuilder: (BuildContext context, int index) {
                          final Color color = _controller.dominantColors[index];
                          final bool isSelected = _controller.selectedColorIndex == index;
                          return GestureDetector(
                            onTap: () => _controller.selectColor(index),
                            child: Container(
                              width: 40,
                              height: 40,
                              margin: const EdgeInsets.only(right: 12),
                              decoration: BoxDecoration(
                                color: color,
                                shape: BoxShape.circle,
                                border: Border.all(
                                  color: isSelected ? Colors.white : Colors.transparent,
                                  width: 2,
                                ),
                                boxShadow: isSelected ? <BoxShadow>[BoxShadow(color: color.withValues(alpha: 0.5), blurRadius: 8)] : null,
                              ),
                            ),
                          );
                        },
                      ),
              ),
            ),
            _buildCircleIcon(Icons.colorize_rounded),
            const SizedBox(width: 12),
            _buildCircleIcon(Icons.visibility_outlined),
          ],
        ),
      ],
    );
  }

  Widget _buildCircleIcon(IconData icon) {
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerHigh,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white10),
      ),
      child: Icon(icon, size: 20, color: AppColors.onSurfaceVariant),
    );
  }

  Widget _buildOverlaySettings() {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('自然颜色过渡', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                Text('Smooth edge blending', style: TextStyle(color: AppColors.onSurfaceVariant.withValues(alpha: 0.7), fontSize: 12)),
              ],
            ),
            Switch(
              value: _controller.useNaturalBlend,
              activeThumbColor: AppColors.accent,
              onChanged: (v) => _controller.useNaturalBlend = v,
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildLayoutSettings() {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text('Mode', style: TextStyle(fontWeight: FontWeight.w500, color: AppColors.onSurfaceVariant)),
            Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  ToggleButton(label: 'Single', active: _controller.isSingleMode, onTap: () => _controller.isSingleMode = true),
                  ToggleButton(label: 'Grid', active: !_controller.isSingleMode, onTap: () => _controller.isSingleMode = false),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),
        if (_controller.isSingleMode) ...[
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('POSITION', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurfaceVariant, letterSpacing: 1.2)),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Text('XY OFFSET', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onSurfaceVariant)),
              ),
            ],
          ),
          const SizedBox(height: 12),
          SliderRow(label: 'X AXIS', value: _controller.singleOffsetX, min: -1, max: 1, onChanged: (v) => _controller.singleOffsetX = v, onReset: () => _controller.singleOffsetX = 0),
          SliderRow(label: 'Y AXIS', value: _controller.singleOffsetY, min: -1, max: 1, onChanged: (v) => _controller.singleOffsetY = v, onReset: () => _controller.singleOffsetY = 0),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('SCALE', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurfaceVariant, letterSpacing: 1.2)),
              Text('${(_controller.singleScale * 100).toInt()}%', style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant)),
            ],
          ),
          Slider(value: _controller.singleScale, min: AppConstants.minScale, max: AppConstants.maxScale, onChanged: (v) => _controller.singleScale = v),
        ],
      ],
    );
  }

  Widget _buildBottomNav() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: const BoxDecoration(
        color: AppColors.background,
        border: Border(top: BorderSide(color: Colors.white10)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _buildNavItem(0, Icons.view_quilt_rounded, 'LAYOUT'),
          _buildNavItem(1, Icons.palette_outlined, 'THEME'),
        ],
      ),
    );
  }

  Widget _buildNavItem(int index, IconData icon, String label) {
    final isActive = _controller.currentTabIndex == index;
    return GestureDetector(
      onTap: () => _controller.currentTabIndex = index,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            decoration: BoxDecoration(
              color: isActive ? AppColors.surfaceContainerHigh : Colors.transparent,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(icon, color: isActive ? AppColors.accent : AppColors.onSurfaceVariant),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.bold,
              color: isActive ? AppColors.onSurface : AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
