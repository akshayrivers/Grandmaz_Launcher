package com.grandma.launcher.ui.contacts

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.grandma.launcher.data.Contact
import com.grandma.launcher.databinding.BottomSheetCallConfirmBinding
import java.io.File

/**
 * Full-screen-ish confirmation before placing a call.
 *
 * Why a confirmation step?
 * Elderly users with tremor or reduced dexterity frequently
 * trigger things accidentally. A confirmation screen means
 * an accidental tap on a contact photo never directly places a call.
 * The user sees the face + name again and has one clear decision.
 *
 * Why BottomSheetDialogFragment?
 * - Slides up from bottom — natural, thumb-reachable
 * - Dismissable by tapping outside (emergency escape)
 * - Shows over the home screen, no navigation needed
 *
 * Why equal-size YES and NO buttons?
 * - Manipulative UI (tiny NO, big YES) is not appropriate for users
 *   who may be confused. Both choices are shown with equal prominence.
 * - Colour differentiates: green = YES, neutral = NO.
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
        val phone = arguments?.getString(ARG_PHONE) ?: return
        val photoPath = arguments?.getString(ARG_PHOTO)

        // Show contact photo or placeholder
        photoPath?.let {
            val file = File(it)
            if (file.exists()) {
                binding.ivConfirmPhoto.setImageBitmap(BitmapFactory.decodeFile(it))
            }
        }

        binding.tvConfirmTitle.text = getString(
            com.grandma.launcher.R.string.confirm_call_title, name
        )

        binding.btnConfirmYes.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }

        binding.btnConfirmNo.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "CallConfirmBottomSheet"
        private const val ARG_NAME = "name"
        private const val ARG_PHONE = "phone"
        private const val ARG_PHOTO = "photo"

        fun show(
            fragmentManager: FragmentManager,
            contact: Contact,
            onConfirm: () -> Unit
        ) {
            val sheet = CallConfirmBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, contact.name)
                    putString(ARG_PHONE, contact.phone)
                    contact.photoPath?.let { putString(ARG_PHOTO, it) }
                }
                this.onConfirm = onConfirm
            }
            sheet.show(fragmentManager, TAG)
        }
    }
}
