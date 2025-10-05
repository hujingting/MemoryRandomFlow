package com.example.photoreviewer

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
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix_ = Matrix()
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

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
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        imageMatrix = matrix_
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetMatrix()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (zoomAnimator?.isRunning == true) {
            return true
        }
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = currentScale * detector.scaleFactor
            if (newScale < MIN_SCALE) {
                resetMatrix()
            } else {
                currentScale = min(newScale, MAX_SCALE)
                matrix_.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                checkAndApplyMatrix()
            }
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > MIN_SCALE) {
                animateZoomTo(MIN_SCALE, width / 2f, height / 2f)
            } else {
                animateZoomTo(MID_SCALE, e.x, e.y)
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (currentScale > MIN_SCALE) {
                matrix_.postTranslate(-distanceX, -distanceY)
                checkAndApplyMatrix()
            }
            return true
        }
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
                        resetMatrix()
                    }
                }
            })
        }
        zoomAnimator?.start()
    }

    private fun resetMatrix() {
        if (drawable == null || width == 0 || height == 0) return
        matrix_.reset()
        drawableRect.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        viewRect.set(0f, 0f, width.toFloat(), height.toFloat())
        matrix_.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER)
        imageMatrix = matrix_
        currentScale = MIN_SCALE
    }

    private fun checkAndApplyMatrix() {
        if (drawable == null) return

        drawableRect.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        matrix_.mapRect(drawableRect)

        val deltaX = getBoundDelta(viewRect.width(), drawableRect.width(), viewRect.centerX(), drawableRect.centerX(), drawableRect.left, viewRect.left, drawableRect.right, viewRect.right)
        val deltaY = getBoundDelta(viewRect.height(), drawableRect.height(), viewRect.centerY(), drawableRect.centerY(), drawableRect.top, viewRect.top, drawableRect.bottom, viewRect.bottom)

        matrix_.postTranslate(deltaX, deltaY)
        imageMatrix = matrix_
    }

    private fun getBoundDelta(viewSize: Float, contentSize: Float, viewCenter: Float, contentCenter: Float, contentLeft: Float, viewLeft: Float, contentRight: Float, viewRight: Float): Float {
        return when {
            contentSize < viewSize -> viewCenter - contentCenter
            contentLeft > viewLeft -> viewLeft - contentLeft
            contentRight < viewRight -> viewRight - contentRight
            else -> 0f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            viewRect.set(0f, 0f, w.toFloat(), h.toFloat())
            resetMatrix()
        }
    }
}
