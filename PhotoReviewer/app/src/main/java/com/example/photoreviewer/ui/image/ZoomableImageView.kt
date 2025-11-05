package com.example.photoreviewer.ui.image

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix_ = Matrix()
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private val scroller: OverScroller

    // Reusable RectF instances to avoid allocations during gestures
    private val drawableRect = RectF()
    private val viewRect = RectF()

    private var currentScale = 1.0f
    private var zoomAnimator: ValueAnimator? = null

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 3.0f
        private const val MID_SCALE = 1.75f
    }

    init {
        scaleType = ScaleType.MATRIX
        scroller = OverScroller(context)
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        imageMatrix = matrix_
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        // 清理之前的缩放动画和状态
        zoomAnimator?.cancel()
        currentScale = 1.0f
        resetMatrix()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (zoomAnimator?.isRunning == true) {
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scroller.forceFinished(true)
        }

        val scaleResult = scaleGestureDetector.onTouchEvent(event)
        val gestureResult = gestureDetector.onTouchEvent(event)

        return scaleResult || gestureResult
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            val matrixValues = FloatArray(9)
            matrix_.getValues(matrixValues)
            val currentTransX = matrixValues[Matrix.MTRANS_X]
            val currentTransY = matrixValues[Matrix.MTRANS_Y]

            val newTransX = scroller.currX.toFloat()
            val newTransY = scroller.currY.toFloat()

            val dx = newTransX - currentTransX
            val dy = newTransY - currentTransY

            matrix_.postTranslate(dx, dy)
            imageMatrix = matrix_
            postInvalidateOnAnimation()
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return true // 允许缩放开始
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (detector.currentSpan <= 0f || detector.scaleFactor <= 0f) {
                return true
            }

            val matrixValues = FloatArray(9)
            matrix_.getValues(matrixValues)
            val actualScale = matrixValues[Matrix.MSCALE_X]

            val newScale = actualScale * detector.scaleFactor

            if (newScale < MIN_SCALE) {
                resetMatrix()
            } else if (newScale <= MAX_SCALE) {
                currentScale = newScale
                matrix_.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                checkAndApplyMatrix()
            }
            // 如果超过最大缩放，不执行任何操作

            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            // 缩放结束时的处理
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > MIN_SCALE) {
                // 当前已缩放，双击重置到适应屏幕
                animateZoomTo(MIN_SCALE, width / 2f, height / 2f)
            } else {
                // 当前是原始大小，双击缩放到填满屏幕
                val targetScale = calculateFitScreenScale()
                animateZoomTo(targetScale, e.x, e.y)
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleGestureDetector.isInProgress || drawable == null) {
                return false
            }
            if (currentScale > MIN_SCALE) {
                // 检查距离值是否有效
                if (distanceX.isFinite() && distanceY.isFinite()) {
                    matrix_.postTranslate(-distanceX, -distanceY)
                    checkAndApplyMatrix()
                }
            }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (currentScale > MIN_SCALE && drawable != null) {
                // 检查速度值是否有效
                if (velocityX.isFinite() && velocityY.isFinite()) {
                    fling((velocityX / 1.5f).toInt(), (velocityY / 1.5f).toInt())
                }
            }
            return true
        }
    }

    private fun fling(velocityX: Int, velocityY: Int) {
        if (drawable == null) return

        val matrixValues = FloatArray(9)
        matrix_.getValues(matrixValues)

        val scale = matrixValues[Matrix.MSCALE_X]
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]

        val contentWidth = drawable.intrinsicWidth * scale
        val contentHeight = drawable.intrinsicHeight * scale

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val minX: Int
        val maxX: Int
        if (contentWidth > viewWidth) {
            minX = (viewWidth - contentWidth).toInt()
            maxX = 0
        } else {
            minX = transX.toInt()
            maxX = transX.toInt()
        }

        val minY: Int
        val maxY: Int
        if (contentHeight > viewHeight) {
            minY = (viewHeight - contentHeight).toInt()
            maxY = 0
        } else {
            minY = transY.toInt()
            maxY = transY.toInt()
        }

        scroller.fling(
            transX.toInt(), transY.toInt(),
            velocityX, velocityY,
            minX, maxX,
            minY, maxY,
            0, 0
        )
        postInvalidateOnAnimation()
    }

    private fun calculateFitScreenScale(): Float {
        if (drawable == null || width == 0 || height == 0) return 1.0f

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (drawableWidth <= 0 || drawableHeight <= 0) return 1.0f

        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        // 计算图片和屏幕的长宽比
        val imageAspectRatio = drawableWidth / drawableHeight
        val screenAspectRatio = screenWidth / screenHeight

        // 根据长宽比决定缩放方式
        val targetScale = if (imageAspectRatio > screenAspectRatio) {
            // 图片比屏幕更宽，缩放到屏幕宽度
            screenWidth / drawableWidth
        } else {
            // 图片比屏幕更高，缩放到屏幕高度
            screenHeight / drawableHeight
        }

        // 确保缩放倍数至少大于 1.0（避免缩放变小）
        val effectiveScale = max(targetScale, 1.1f)

        // 限制最大缩放倍数，避免过度放大
        return min(effectiveScale, MAX_SCALE)
    }

    private fun animateZoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        zoomAnimator?.cancel()

        val startScale = currentScale
        zoomAnimator = ValueAnimator.ofFloat(startScale, targetScale).apply {
            duration = 300
            addUpdateListener { valueAnimator ->
                val newScale = valueAnimator.animatedValue as Float
                val scaleFactor = newScale / currentScale
                currentScale = newScale

                matrix_.postScale(scaleFactor, scaleFactor, focusX, focusY)
                checkAndApplyMatrix()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (targetScale == MIN_SCALE) {
                        currentScale = MIN_SCALE
                        resetMatrix()
                    } else {
                        currentScale = targetScale
                    }
                }
            })
        }
        zoomAnimator?.start()
    }

    private fun resetMatrix() {
        if (drawable == null || width == 0 || height == 0) return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        // 检查 drawable 尺寸是否有效
        if (drawableWidth <= 0 || drawableHeight <= 0) return

        matrix_.reset()
        drawableRect.set(0f, 0f, drawableWidth, drawableHeight)
        viewRect.set(0f, 0f, width.toFloat(), height.toFloat())
        matrix_.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER)
        imageMatrix = matrix_

        // 更新 currentScale 为实际的矩阵缩放值
        val matrixValues = FloatArray(9)
        matrix_.getValues(matrixValues)
        currentScale = matrixValues[Matrix.MSCALE_X]
    }

    private fun checkAndApplyMatrix() {
        if (drawable == null) return

        val matrixValues = FloatArray(9)
        matrix_.getValues(matrixValues)

        val scale = matrixValues[Matrix.MSCALE_X]
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]

        val contentWidth = drawable.intrinsicWidth * scale
        val contentHeight = drawable.intrinsicHeight * scale

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        var deltaX = 0f
        if (contentWidth <= viewWidth) {
            deltaX = viewWidth / 2f - (transX + contentWidth / 2f)
        } else if (transX > 0) {
            deltaX = -transX
        } else if (transX + contentWidth < viewWidth) {
            deltaX = viewWidth - (transX + contentWidth)
        }

        var deltaY = 0f
        if (contentHeight <= viewHeight) {
            deltaY = viewHeight / 2f - (transY + contentHeight / 2f)
        } else if (transY > 0) {
            deltaY = -transY
        } else if (transY + contentHeight < viewHeight) {
            deltaY = viewHeight - (transY + contentHeight)
        }

        matrix_.postTranslate(deltaX, deltaY)
        imageMatrix = matrix_
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // 避免重用 viewRect，创建新的实例
            val newViewRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            viewRect.set(newViewRect)
            resetMatrix()
        }
    }

    // 添加内存清理方法
    fun cleanup() {
        zoomAnimator?.cancel()
        zoomAnimator = null
        // 清理矩阵以释放资源
        matrix_.reset()
        // 清理 RectF 对象
        drawableRect.setEmpty()
        viewRect.setEmpty()
        // 重置状态
        currentScale = 1.0f
    }

    // 重置到安全状态
    fun resetToSafeState() {
        zoomAnimator?.cancel()
        currentScale = 1.0f
        if (drawable != null) {
            resetMatrix()
        }
    }
}