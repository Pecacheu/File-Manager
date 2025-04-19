package org.fossify.filemanager.extensions

import android.content.Context
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.otgPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.filemanager.helpers.REMOTE_URI
import java.util.Base64

fun String.isZipFile() = endsWith(".zip", true)

fun String.getBasePath(context: Context): String {
	return when {
		startsWith(REMOTE_URI) -> substring(0, indexOf(':')+1)
		startsWith(context.internalStoragePath) -> context.internalStoragePath
		context.isPathOnSD(this) -> context.sdCardPath
		context.isPathOnOTG(this) -> context.otgPath
		else -> "/"
	}
}

fun ByteArray.toBase64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
fun String.fromBase64(): ByteArray = Base64.getUrlDecoder().decode(this)