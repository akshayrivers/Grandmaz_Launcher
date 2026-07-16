package com.grandma.launcher.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.grandma.launcher.databinding.ViewAddContactCardBinding

/**
 * The "Add Contact" slot that appears when there are fewer than
 * MAX_HOME_CONTACTS contacts on the home screen.
 *
 * Uses a dashed border and muted colours to visually signal
 * "this is an empty slot" vs a filled contact card.
 */
class AddContactCardView(context: Context) : FrameLayout(context) {

    private val binding: ViewAddContactCardBinding =
        ViewAddContactCardBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        setupTouchFeedback()
    }

    private fun setupTouchFeedback() {
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            false
        }
    }
}
