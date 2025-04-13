package org.fossify.filemanager.extensions

import android.content.Context
import android.os.storage.StorageManager
import org.fossify.commons.R
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.otgPath
import org.fossify.filemanager.helpers.Config
import org.fossify.filemanager.helpers.PRIMARY_VOLUME_NAME
import org.fossify.filemanager.helpers.REMOTE_URI
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.time.Instant
import java.util.Base64
import java.util.Locale
import kotlin.random.Random

val Context.config: Config get() = Config.newInstance(applicationContext)

var IDCount: UByte = 0u

//From Snap UUID Generator v1.1 by Pecacheu
class UUID(val id: ByteArray) {
	companion object {
		fun from(id: String) = UUID(Base64.getUrlDecoder().decode(id))

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

	override fun toString(): String {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(id)
	}
}

fun Context.isPathOnRoot(path: String) = !(path.startsWith(config.internalStoragePath) || isPathOnOTG(path) || (isPathOnSD(path)))
fun Context.isRemotePath(path: String) = path.startsWith(REMOTE_URI)
fun Context.idFromRemotePath(path: String) = path.substring(REMOTE_URI.length, path.indexOf(':'))

fun Context.getHumanReadablePath(path: String): String {
	return when {
		path == "/" -> getString(R.string.root)
		path == internalStoragePath -> getString(R.string.internal)
		path == otgPath -> getString(R.string.usb)
		isRemotePath(path) -> config.getRemote(idFromRemotePath(path))?.name?:getString(R.string.unknown)
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