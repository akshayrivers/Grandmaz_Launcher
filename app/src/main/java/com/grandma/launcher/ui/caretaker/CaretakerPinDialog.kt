package com.grandma.launcher.ui.caretaker

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import com.grandma.launcher.R
import com.grandma.launcher.appScope
import com.grandma.launcher.data.PinHasher
import com.grandma.launcher.data.PinManager
import com.grandma.launcher.databinding.DialogCaretakerPinBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CaretakerPinDialog {

    fun show(context: Context, onSuccess: () -> Unit) {
        val pinManager = PinManager(context)

        context.appScope.launch {
            val pinSet = pinManager.isPinSet()
            withContext(Dispatchers.Main) {
                if ((context as? Activity)?.isFinishing == true) return@withContext
                // No PIN has ever been configured (setup not finished): nothing to protect,
                // and setup itself will require one.
                if (!pinSet) onSuccess() else showPrompt(context, pinManager, onSuccess)
            }
        }
    }

    private fun showPrompt(context: Context, pinManager: PinManager, onSuccess: () -> Unit) {
        val binding = DialogCaretakerPinBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .setPositiveButton(R.string.pin_dialog_confirm, null)
            .setNegativeButton(R.string.pin_dialog_cancel, null)
            .create()

        var checking = false
        fun submit() {
            if (checking) return
            val input = binding.etPinInput.text?.toString().orEmpty()
            if (!PinHasher.isValidFormat(input)) {
                binding.tvPinError.visibility = View.VISIBLE
                return
            }
            checking = true
            context.appScope.launch {
                val ok = pinManager.verify(input)
                withContext(Dispatchers.Main) {
                    checking = false
                    if (ok) {
                        dialog.dismiss()
                        onSuccess()
                    } else {
                        binding.tvPinError.visibility = View.VISIBLE
                        binding.etPinInput.setText("")
                    }
                }
            }
        }

        binding.etPinInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvPinError.visibility = View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.etPinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
            binding.etPinInput.requestFocus()
        }
        dialog.show()
    }
}
