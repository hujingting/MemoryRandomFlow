import 'dart:io';
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
                child: Center(
                  child: _controller.images.isEmpty ? _buildEmptyState() : _buildPreview(aspectRatio),
                ),
              ),
              if (_controller.images.isNotEmpty) _buildControlPanel(),
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

  Widget _buildPreview(double aspectRatio) {
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
            padding: EdgeInsets.all(_controller.isSingleMode ? 0 : _controller.spacing),
            child: _controller.isSingleMode ? _buildSingleImagePreview() : _buildGridPreview(),
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

  Widget _buildGridPreview() {
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
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildSingleImagePreview() {
    final XFile file = _controller.images.first;
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
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildControlPanel() {
    return AnimatedSize(
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
      child: _controller.isPanelVisible
          ? GestureDetector(
              onVerticalDragUpdate: (details) {
                if (details.primaryDelta! > 10) {
                  _controller.isPanelVisible = false;
                }
              },
              child: Container(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainer.withValues(alpha: 0.95),
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
                  border: const Border(top: BorderSide(color: Colors.white10)),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 40,
                      height: 4,
                      margin: const EdgeInsets.only(bottom: 20),
                      decoration: BoxDecoration(
                        color: Colors.white10,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    if (_controller.currentTabIndex == 0) _buildGridSettings(),
                    if (_controller.currentTabIndex == 1) _buildThemeSettings(),
                    if (_controller.currentTabIndex == 2) _buildOverlaySettings(),
                    if (_controller.currentTabIndex == 3) _buildLayoutSettings(),
                    const SizedBox(height: 8),
                  ],
                ),
              ),
            )
          : GestureDetector(
              onTap: () => _controller.isPanelVisible = true,
              onVerticalDragUpdate: (details) {
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
            ),
    );
  }

  Widget _buildGridSettings() {
    return Column(
      children: [
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
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  itemCount: _controller.dominantColors.length,
                  itemBuilder: (context, index) {
                    final color = _controller.dominantColors[index];
                    final isSelected = _controller.selectedColorIndex == index;
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
                          boxShadow: isSelected ? [BoxShadow(color: color.withValues(alpha: 0.5), blurRadius: 8)] : null,
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
              activeColor: AppColors.accent,
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
          _buildNavItem(0, Icons.grid_view_rounded, 'GRID'),
          _buildNavItem(1, Icons.palette_outlined, 'THEME'),
          _buildNavItem(2, Icons.layers_outlined, 'OVERLAY'),
          _buildNavItem(3, Icons.aspect_ratio_outlined, 'LAYOUT'),
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
