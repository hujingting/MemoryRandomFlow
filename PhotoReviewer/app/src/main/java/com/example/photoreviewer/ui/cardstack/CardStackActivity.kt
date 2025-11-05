package com.example.photoreviewer.ui.cardstack

import android.animation.ObjectAnimator
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.photoreviewer.databinding.ActivityCardStackBinding

class CardStackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCardStackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardStackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        animateCards()
    }

    private fun animateCards() {
        val card1 = binding.cardView1
        val card2 = binding.cardView2
        val card3 = binding.cardView3

        // Initial positions (off-screen bottom)
        card1.translationY = 2000f
        card2.translationY = 2000f
        card3.translationY = 2000f

        // Animate card 1 (top card)
        ObjectAnimator.ofFloat(card1, "translationY", -200f).apply {
            duration = 800
            startDelay = 300
            start()
        }

        ObjectAnimator.ofFloat(card1, "rotation", 0f, 5f).apply {
            duration = 800
            startDelay = 600
            start()
        }

        // Animate card 2 (left card)
        ObjectAnimator.ofFloat(card2, "translationY", 300f).apply {
            duration = 800
            startDelay = 200
            start()
        }
        ObjectAnimator.ofFloat(card2, "translationX", 0f, -400f).apply {
            duration = 800
            startDelay = 200
            start()
        }
        ObjectAnimator.ofFloat(card2, "rotation", 0f, -25f).apply {
            duration = 800
            startDelay = 600
            start()
        }

        // Animate card 3 (right card)
        ObjectAnimator.ofFloat(card3, "translationY", 300f).apply {
            duration = 800
            startDelay = 300
            start()
        }
        ObjectAnimator.ofFloat(card3, "translationX", 0f, 400f).apply {
            duration = 800
            startDelay = 300
            start()
        }
        ObjectAnimator.ofFloat(card3, "rotation", 0f, 25f).apply {
            duration = 800
            startDelay = 600
            start()
        }
    }

}
