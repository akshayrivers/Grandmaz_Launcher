package com.grandma.launcher.ui.assistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.grandma.launcher.ui.common.InstalledApp

/**
 * Ask leaves this app and opens Gemini on the phone. There is no in-app model
 * and no call to the Gemini API. A missing Gemini app opens its Play Store page.
 */
class TalkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InstalledApp.openGemini(this)
        finish()
    }
}
