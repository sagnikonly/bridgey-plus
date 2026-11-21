package dev.bridgey.android

import android.content.ClipDescription
import org.json.JSONObject

internal const val MAX_CLIPBOARD_CONTENT_BYTES = 32 * 1024
private const val EXTRA_IS_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"

internal data class ClipboardSnapshot(val text: String, val html: String?)

internal fun isSensitiveClip(description: ClipDescription?): Boolean {
    val extras = description?.extras ?: return false
    return extras.getBoolean(EXTRA_IS_SENSITIVE_KEY, false)
}

internal data class RichClipboardContent(val text: String, val html: String) {
    fun encode(): ByteArray = JSONObject()
        .put("version", 1)
        .put("text", text)
        .put("html", html)
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        fun create(text: String, html: String?): RichClipboardContent? {
            val rich = html?.takeIf(String::isNotBlank) ?: return null
            if (text.isEmpty() || text.toByteArray().size + rich.toByteArray().size > MAX_CLIPBOARD_CONTENT_BYTES) return null
            return RichClipboardContent(text, rich)
        }

        fun decode(bytes: ByteArray): RichClipboardContent? {
            val objectValue = strictJsonObject(bytes) ?: return null
            if (jsonInteger(objectValue.opt("version")) != 1L) return null
            val text = objectValue.opt("text") as? String ?: return null
            val html = objectValue.opt("html") as? String ?: return null
            return create(text, html)
        }
    }
}

internal fun clipboardTextFits(text: String): Boolean =
    text.isNotEmpty() && text.toByteArray(Charsets.UTF_8).size <= MAX_CLIPBOARD_CONTENT_BYTES
