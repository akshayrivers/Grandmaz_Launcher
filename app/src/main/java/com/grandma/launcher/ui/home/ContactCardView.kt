package com.grandma.launcher.ui.home

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import com.grandma.launcher.R
import android.view.MotionEvent
import android.widget.FrameLayout
import com.grandma.launcher.data.Contact
import com.grandma.launcher.databinding.ViewContactCardBinding
import java.io.File

/**
 * Full-bleed contact card for the home screen.
 *
 * Photo fills the entire card (centerCrop).
 * Name overlays the bottom with a dark gradient scrim behind it
 * so it's readable regardless of photo brightness.
 *
 * No-photo fallback: ink fill with the contrasting label colour.
 */
class ContactCardView(
    context: Context,
    private val contact: Contact
) : FrameLayout(context) {

    private val binding = ViewContactCardBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    var onTap: (() -> Unit)? = null

    init {
        setupCard()
        setupTouchFeedback()
    }

    private fun setupCard() {
        val photoFile = contact.photoPath?.let { File(it) }

        if (photoFile != null && photoFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            if (bitmap != null) {
                binding.ivContactPhoto.setImageBitmap(bitmap)
            } else {
                showPlaceholder()
            }
        } else {
            showPlaceholder()
        }

        binding.tvContactName.text = contact.name
        setOnClickListener { onTap?.invoke() }
    }

    private fun showPlaceholder() {
        binding.ivContactPhoto.setBackgroundColor(
            ContextCompat.getColor(context, R.color.color_text_primary)
        )
        binding.ivContactPhoto.setImageDrawable(null)
        binding.nameScrim.alpha = 0f
        binding.tvContactName.setTextColor(
            ContextCompat.getColor(context, R.color.color_text_on_dark)
        )
        binding.tvContactName.setShadowLayer(0f, 0f, 0f, 0)
    }

    private fun setupTouchFeedback() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }
}
