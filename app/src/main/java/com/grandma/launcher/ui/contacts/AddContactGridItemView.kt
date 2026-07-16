package com.grandma.launcher.ui.contacts

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import com.grandma.launcher.databinding.ViewAddContactCardBinding

/**
 * "Add Contact" slot for the all-contacts grid.
 * Same visual as the home screen add slot.
 */
class AddContactGridItemView(context: Context) : FrameLayout(context) {

    init {
        ViewAddContactCardBinding.inflate(LayoutInflater.from(context), this, true)
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }
}
