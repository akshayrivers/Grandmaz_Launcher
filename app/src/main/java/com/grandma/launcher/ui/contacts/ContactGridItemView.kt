package com.grandma.launcher.ui.contacts

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import com.grandma.launcher.data.Contact
import com.grandma.launcher.databinding.ViewContactCardBinding
import java.io.File

/**
 * Contact card for the all-contacts grid screen.
 * Same visual style as the home contact card but used in 3-per-row layout.
 * Supports both tap (call confirm) and long press (options sheet).
 */
class ContactGridItemView(
    context: Context,
    private val contact: Contact
) : FrameLayout(context) {

    private val binding = ViewContactCardBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    init {
        // Photo
        val photoFile = contact.photoPath?.let { File(it) }
        if (photoFile != null && photoFile.exists()) {
            binding.ivContactPhoto.setImageBitmap(BitmapFactory.decodeFile(photoFile.absolutePath))
        }
        binding.tvContactName.text = contact.name

        setOnClickListener { onTap?.invoke() }

        setOnLongClickListener {
            onLongPress?.invoke()
            true
        }

        // Scale feedback
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
