package com.grandma.launcher.voice

import androidx.annotation.StringRes
import com.grandma.launcher.R

/** Button words a caretaker can replace with a recording. Dogri only exists this way. */
object VoiceSlots {
    const val HOME = "home"
    const val PEOPLE = "people"
    const val WEATHER = "weather"
    const val ADD_PERSON = "add_person"
    const val ASK = "ask"
    const val APPS = "apps"
    const val CAMERA = "camera"
    const val WHATSAPP = "whatsapp"
    const val CALL = "call"
    const val NO = "no"
    const val HELP = "help"
    const val SOS = "sos"
    const val TAKEN = "taken"
    const val LATER = "later"
    const val MISSED = "missed"

    data class Slot(val id: String, @StringRes val label: Int, @StringRes val spoken: Int)

    val all = listOf(
        Slot(HOME, R.string.slot_home, R.string.spoken_home),
        Slot(PEOPLE, R.string.slot_people, R.string.spoken_people),
        Slot(WEATHER, R.string.slot_weather, R.string.spoken_weather),
        Slot(ADD_PERSON, R.string.slot_add_person, R.string.spoken_add_person),
        Slot(ASK, R.string.slot_ask, R.string.spoken_ask),
        Slot(APPS, R.string.slot_apps, R.string.spoken_apps),
        Slot(CAMERA, R.string.slot_camera, R.string.spoken_camera),
        Slot(WHATSAPP, R.string.slot_whatsapp, R.string.spoken_whatsapp),
        Slot(CALL, R.string.slot_call, R.string.spoken_call),
        Slot(NO, R.string.slot_no, R.string.spoken_no),
        Slot(HELP, R.string.slot_help, R.string.spoken_help),
        Slot(SOS, R.string.slot_sos, R.string.spoken_sos),
        Slot(TAKEN, R.string.slot_taken, R.string.spoken_taken),
        Slot(LATER, R.string.slot_later, R.string.spoken_later),
        Slot(MISSED, R.string.slot_missed, R.string.spoken_missed)
    )
}
