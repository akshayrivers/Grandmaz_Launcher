package com.grandma.launcher.ui.home

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.grandma.launcher.R
import com.grandma.launcher.data.Contact
import com.grandma.launcher.databinding.ViewContactCardBinding
import java.io.File

/**
 * A single contact card for the home screen.
 *
 * Layout: circular photo (top 60%) + name label (bottom 40%).
 * The photo is loaded from the app's private file storage.
 * If no photo, shows the contact's initial on a coloured background.
 *
 * Touch feedback: scale to 0.95 on press, return on release.
 * This gives a clear tactile-feel-equivalent visual response
 * without a ripple (which can be confusing on photo content).
 */
class ContactCardView(
    context: Context,
    private val contact: Contact
) : FrameLayout(context) {

    private val binding: ViewContactCardBinding =
        ViewContactCardBinding.inflate(LayoutInflater.from(context), this, true)

    var onTap: (() -> Unit)? = null

    init {
        setupCard()
        setupTouchFeedback()
    }

    private fun setupCard() {
        // Load photo or show initial
        val photoFile = contact.photoPath?.let { File(it) }
        if (photoFile != null && photoFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            binding.ivContactPhoto.setImageBitmap(bitmap)
        } else {
            // No photo — show initial letter on coloured circle
            binding.ivContactPhoto.setImageDrawable(
                createInitialDrawable(contact.name)
            )
        }

        binding.tvContactName.text = contact.name
        setOnClickListener { onTap?.invoke() }
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
            false // Let click listener still fire
        }
    }

    private fun createInitialDrawable(name: String): Drawable {
        // Return a simple coloured circle with the first letter.
        // This is a placeholder — in production we'd use a custom drawable
        // or a library. For Phase 1 this is sufficient.
        return ContextCompat.getDrawable(context, R.drawable.bg_contact_placeholder)
            ?: ContextCompat.getDrawable(context, android.R.drawable.ic_menu_myplaces)!!
    }
}
