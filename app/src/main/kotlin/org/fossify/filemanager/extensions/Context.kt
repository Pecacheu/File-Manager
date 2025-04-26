package org.fossify.filemanager.extensions

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.storage.StorageManager
import android.util.Log
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.R
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.otgPath
import org.fossify.filemanager.App
import org.fossify.filemanager.helpers.Config
import org.fossify.filemanager.helpers.PRIMARY_VOLUME_NAME
import org.fossify.filemanager.helpers.REMOTE_URI
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.time.Instant
import java.util.Locale
import kotlin.random.Random

val Context.config: Config get() = (this.applicationContext as App).conf

var IDCount: UByte = 0u

//From Snap UUID Generator v1.1 by Pecacheu
class UUID(val id: ByteArray) {
	companion object {
		const val LENGTH = 11
		fun from(id: String) = UUID(id.fromBase64())
		fun genUUID(): UUID {
			val uid = ByteBuffer.allocate(8).order(LITTLE_ENDIAN)
			uid.put(Random.Default.nextBytes(2))
			uid.put(System.currentTimeMillis().toUByte().toByte())
			uid.put(IDCount.toByte())
			uid.putInt((Instant.now().toEpochMilli()/10000L).toUInt().toInt())
			//We've got 1361 years until this overflows, baby
			++IDCount
			return UUID(uid.array())
		}
	}

	init {
		if(id.size != 8) throw ArrayIndexOutOfBoundsException()
	}

	override fun toString(): String = id.toBase64()
	override fun hashCode() = id.contentHashCode()
	override operator fun equals(other: Any?) = id == (other as? UUID)?.id
}

fun Context.isPathOnRoot(path: String) = !(path.startsWith(config.internalStoragePath) || isPathOnOTG(path) || (isPathOnSD(path)))
fun isRemotePath(path: String) = path.startsWith(REMOTE_URI)
fun idFromRemotePath(path: String) = path.substring(REMOTE_URI.length, path.indexOf(':'))

fun Context.getHumanReadablePath(path: String): String {
	return when {
		path == "/" -> getString(R.string.root)
		path == internalStoragePath -> getString(R.string.internal)
		path == otgPath -> getString(R.string.usb)
		isRemotePath(path) -> config.getRemoteForPath(path)?.name?:getString(R.string.unknown)
		else -> getString(R.string.sd_card)
	}
}

fun Context.humanizePath(path: String): String {
	val trimPath = path.trimEnd('/')
	val basePath = path.getBasePath(this)
	val repPath = getHumanReadablePath(basePath)
	return when(basePath) {
		"/" -> "${repPath}$trimPath"
		else -> trimPath.replaceFirst(basePath, repPath)
	}
}

fun Context.getAllVolumeNames(): List<String> {
	val volumeNames = mutableListOf(PRIMARY_VOLUME_NAME)
	val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
	getExternalFilesDirs(null).mapNotNull {storageManager.getStorageVolume(it)}.filterNot {it.isPrimary}.mapNotNull {it.uuid?.lowercase(Locale.US)}.forEach {
		volumeNames.add(it)
	}
	return volumeNames
}

fun Activity.error(e: Throwable, prompt: String?=null, cb: ((res: Boolean)->Unit)?=null) {
	Log.e("files", "Error", e)
	var es = if(e::class == Error::class) e.message?:getString(R.string.unknown_error_occurred) else e.toString()
	if(prompt != null) es += "\n\n$prompt"
	alert("Error", es, cb)
}
fun Activity.alert(title: String, msg: String, cb: ((res: Boolean)->Unit)?=null) {
	runOnUiThread {
		val diag = getAlertDialogBuilder().create()
		diag.setTitle(title)
		diag.setMessage(msg)
		val clk = object: DialogInterface.OnClickListener {
			override fun onClick(dialog: DialogInterface, which: Int) {
				diag.dismiss()
				cb?.invoke(which == AlertDialog.BUTTON_POSITIVE)
			}
		}
		diag.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), clk)
		if(cb != null) diag.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel), clk)
		diag.show()
	}
}