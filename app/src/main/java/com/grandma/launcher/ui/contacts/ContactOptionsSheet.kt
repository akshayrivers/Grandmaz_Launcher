package com.grandma.launcher.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.grandma.launcher.data.Contact
import com.grandma.launcher.databinding.BottomSheetContactOptionsBinding

/**
 * Long-press options sheet for a contact.
 * Slides up from bottom — thumb-reachable.
 * Options: Call, Edit, Remove, Cancel.
 *
 * All rows are 72dp tall — consistent, tremor-safe.
 */
class ContactOptionsSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetContactOptionsBinding? = null
    private val binding get() = _binding!!

    private var onEdit: (() -> Unit)? = null
    private var onRemove: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetContactOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = arguments?.getString(ARG_NAME) ?: ""
        binding.tvOptionsTitle.text = name

        binding.btnEdit.setOnClickListener {
            onEdit?.invoke()
            dismiss()
        }

        binding.btnRemove.setOnClickListener {
            onRemove?.invoke()
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ContactOptionsSheet"
        private const val ARG_NAME = "name"

        fun show(
            fragmentManager: FragmentManager,
            contact: Contact,
            onEdit: () -> Unit,
            onRemove: () -> Unit
        ) {
            val sheet = ContactOptionsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, contact.name)
                }
                this.onEdit = onEdit
                this.onRemove = onRemove
            }
            sheet.show(fragmentManager, TAG)
        }
    }
}
