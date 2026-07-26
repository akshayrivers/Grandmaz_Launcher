package com.grandma.launcher.ui.caretaker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.databinding.DialogCaretakerPinBinding

object CaretakerPinDialog {

    fun show(context: Context, onSuccess: () -> Unit) {
        val appPrefs = AppPreferences(context)
        
        // If no PIN is configured, bypass authentication directly
        if (appPrefs.caretakerPin.isEmpty()) {
            onSuccess()
            return
        }

        val binding = DialogCaretakerPinBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        binding.etPinInput.requestFocus()

        // Handle auto-submit on 4 digits
        binding.etPinInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvPinError.visibility = View.GONE
                if (s?.length == 4) {
                    val input = s.toString().trim()
                    if (appPrefs.verifyPin(input)) {
                        dialog.dismiss()
                        onSuccess()
                    } else {
                        binding.tvPinError.visibility = View.VISIBLE
                        binding.etPinInput.setText("")
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        dialog.show()
    }
}
