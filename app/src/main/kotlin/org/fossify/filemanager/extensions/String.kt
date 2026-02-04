package org.fossify.filemanager.extensions

import android.content.Context
import org.fossify.filemanager.helpers.REMOTE_URI
import org.fossify.filemanager.helpers.Remote
import java.util.Base64

fun String.isZipFile() = endsWith(".zip", true)

fun String.isPathInHiddenFolder(): Boolean {
	val parts = split("/")
	for (i in 1 until parts.size - 1) {
		val part = parts[i]
		val isHidden = part.startsWith(".") && part != "." && part != ".." && part.isNotEmpty()
		if (isHidden) {
			return true
		}
	}
	return false
}

fun String.getBasePath(context: Context): String {
	return when {
		startsWith(REMOTE_URI) -> substring(0, Remote.URI_BASE)
		startsWith(context.config.internalStoragePath) -> context.config.internalStoragePath
		context.isPathOnSD(this) -> context.config.sdCardPath
		context.isPathOnOTG(this) -> context.config.OTGPath
		else -> "/"
	}
}

fun ByteArray.toBase64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
fun String.fromBase64(): ByteArray = Base64.getUrlDecoder().decode(this)