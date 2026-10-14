package com.grandma.launcher.remote

import org.json.JSONObject

/**
 * Caretaker command union from GET /api/device/commands (`{ id, type, payload }`).
 * Anything unknown or malformed parses to [Invalid] and is reported back as failed.
 */
sealed class DeviceCommand {
    abstract val id: String

    data class RingDevice(override val id: String) : DeviceCommand()
    data class SyncNow(override val id: String) : DeviceCommand()
    data class ReportLocation(override val id: String) : DeviceCommand()
    data class ShowMessage(override val id: String, val text: String, val voiceClipId: String?) : DeviceCommand()
    data class SetVolume(override val id: String, val level: Int) : DeviceCommand()
    data class UpdateEmergencyNumber(override val id: String, val e164: String) : DeviceCommand()
    data class Invalid(override val id: String, val type: String, val reason: String) : DeviceCommand()

    companion object {
        const val TYPE_RING_DEVICE = "ring_device"
        const val TYPE_SYNC_NOW = "sync_now"
        const val TYPE_REPORT_LOCATION = "report_location"
        const val TYPE_SHOW_MESSAGE = "show_message"
        const val TYPE_SET_VOLUME = "set_volume"
        const val TYPE_UPDATE_EMERGENCY_NUMBER = "update_emergency_number"

        const val MAX_MESSAGE_LENGTH = 500
        private val E164 = Regex("^\\+[1-9]\\d{6,14}$")

        /** Returns null only when the command has no id (cannot even be reported). */
        fun parse(json: JSONObject): DeviceCommand? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            val type = json.optString("type")
            val payload = json.optJSONObject("payload") ?: JSONObject()

            return when (type) {
                TYPE_RING_DEVICE -> RingDevice(id)
                TYPE_SYNC_NOW -> SyncNow(id)
                TYPE_REPORT_LOCATION -> ReportLocation(id)
                TYPE_SHOW_MESSAGE -> {
                    val text = if (payload.isNull("text")) "" else payload.optString("text").trim()
                    when {
                        text.isEmpty() -> Invalid(id, type, "Message text is empty")
                        text.length > MAX_MESSAGE_LENGTH -> Invalid(id, type, "Message text is longer than $MAX_MESSAGE_LENGTH characters")
                        else -> ShowMessage(id, text, payload.optString("voiceClipId").takeIf { it.isNotBlank() && !payload.isNull("voiceClipId") })
                    }
                }
                TYPE_SET_VOLUME -> {
                    val raw = payload.opt("level")
                    val level = (raw as? Number)?.toDouble()
                    if (level == null || level != Math.floor(level) || level < 0 || level > 100) {
                        Invalid(id, type, "Volume level must be an integer 0-100")
                    } else {
                        SetVolume(id, level.toInt())
                    }
                }
                TYPE_UPDATE_EMERGENCY_NUMBER -> {
                    val e164 = payload.optString("e164").trim()
                    if (E164.matches(e164)) UpdateEmergencyNumber(id, e164)
                    else Invalid(id, type, "Emergency number must be in E.164 format")
                }
                else -> Invalid(id, type, "Unknown or unsupported command: ${type.ifBlank { "(none)" }}")
            }
        }
    }
}

data class CommandResult(
    val success: Boolean,
    val message: String,
    val extras: Map<String, Any> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("success", success)
        put("message", message)
        extras.forEach { (k, v) -> put(k, v) }
    }

    companion object {
        fun ok(message: String, extras: Map<String, Any> = emptyMap()) = CommandResult(true, message, extras)
        fun failed(message: String) = CommandResult(false, message)
    }
}
