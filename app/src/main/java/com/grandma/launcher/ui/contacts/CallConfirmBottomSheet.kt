package com.grandma.launcher.ui.contacts

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.grandma.launcher.R
import com.grandma.launcher.data.Contact
import com.grandma.launcher.databinding.BottomSheetCallConfirmBinding
import com.grandma.launcher.voice.Speaker
import com.grandma.launcher.voice.VoiceSlots
import java.io.File

/**
 * Confirmation before a call. The sheet says her name. The phone button
 * says "call" only after she presses it. Both buttons are the same size.
 */
class CallConfirmBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCallConfirmBinding? = null
    private val binding get() = _binding!!
    private var onConfirm: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCallConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = arguments?.getString(ARG_NAME) ?: return
        val photoPath = arguments?.getString(ARG_PHOTO)
        val clipId = arguments?.getString(ARG_CLIP)
        val whatsapp = arguments?.getBoolean(ARG_WHATSAPP) == true

        photoPath?.let {
            val file = File(it)
            if (file.exists()) {
                binding.ivConfirmPhoto.setImageBitmap(BitmapFactory.decodeFile(it))
            }
        }

        binding.tvConfirmTitle.text = name
        if (whatsapp) binding.btnConfirmYes.setImageResource(R.drawable.ic_nav_chat)

        say(name, clipId)

        binding.btnConfirmYes.setOnClickListener {
            (activity as? Speaker)?.speakSlot(VoiceSlots.CALL, getString(R.string.spoken_call))
            onConfirm?.invoke()
            dismiss()
        }

        binding.btnConfirmNo.setOnClickListener {
            (activity as? Speaker)?.speakSlot(VoiceSlots.NO, getString(R.string.spoken_no))
            dismiss()
        }
    }

    private fun say(text: String, clipId: String? = null) {
        (activity as? Speaker)?.speak(text, clipId)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CallConfirmBottomSheet"
        private const val ARG_NAME = "name"
        private const val ARG_PHONE = "phone"
        private const val ARG_PHOTO = "photo"
        private const val ARG_CLIP = "clip"
        private const val ARG_WHATSAPP = "whatsapp"

        fun show(
            fragmentManager: FragmentManager,
            contact: Contact,
            onConfirm: () -> Unit
        ) {
            val sheet = CallConfirmBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, contact.name)
                    putString(ARG_PHONE, contact.phone)
                    putString(ARG_CLIP, contact.nameClipId)
                    putBoolean(ARG_WHATSAPP, contact.whatsapp)
                    contact.photoPath?.let { putString(ARG_PHOTO, it) }
                }
                this.onConfirm = onConfirm
            }
            sheet.show(fragmentManager, TAG)
        }
    }
}
