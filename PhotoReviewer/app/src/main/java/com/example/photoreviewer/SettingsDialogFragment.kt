package com.example.photoreviewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.DialogFragment
import kotlin.math.hypot

class SettingsDialogFragment : DialogFragment() {

    private lateinit var rootView: View
    private lateinit var contentView: View

    companion object {
        const val TAG = "SettingsDialogFragment"
        private const val ARG_FAB_X = "fab_x"
        private const val ARG_FAB_Y = "fab_y"

        fun newInstance(fabX: Int, fabY: Int): SettingsDialogFragment {
            val args = Bundle()
            args.putInt(ARG_FAB_X, fabX)
            args.putInt(ARG_FAB_Y, fabY)
            val fragment = SettingsDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full screen, no title, transparent background to show reveal animation
        setStyle(STYLE_NO_FRAME, R.style.Theme_App_Transparent)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view
        contentView = view.findViewById(R.id.settings_content_view)

        if (savedInstanceState == null) {
            rootView.visibility = View.INVISIBLE
            val fabX = arguments?.getInt(ARG_FAB_X) ?: 0
            val fabY = arguments?.getInt(ARG_FAB_Y) ?: 0

            rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    enterAnimation(fabX, fabY)
                }
            })
        }
    }

    private fun enterAnimation(fabX: Int, fabY: Int) {
        val finalRadius = hypot(rootView.width.toDouble(), rootView.height.toDouble()).toFloat()
        val circularReveal = android.view.ViewAnimationUtils.createCircularReveal(rootView, fabX, fabY, 0f, finalRadius)
        circularReveal.duration = 500L
        circularReveal.interpolator = AccelerateDecelerateInterpolator()

        rootView.visibility = View.VISIBLE
        circularReveal.start()

        // Animate content
        contentView.apply {
            alpha = 0f
            translationY = 40f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350L)
                .setStartDelay(150L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }
}
