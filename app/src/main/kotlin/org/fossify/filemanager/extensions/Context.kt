package org.fossify.filemanager.extensions

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.storage.StorageManager
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import org.fossify.commons.R
import org.fossify.commons.compose.extensions.getActivity
import org.fossify.commons.extensions.DIRS_ACCESSIBLE_ONLY_WITH_SAF
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.helpers.isRPlus
import org.fossify.filemanager.App
import org.fossify.filemanager.helpers.Config
import org.fossify.filemanager.helpers.PRIMARY_VOLUME_NAME
import org.fossify.filemanager.helpers.REMOTE_URI
import org.fossify.filemanager.helpers.Remote
import java.io.FileNotFoundException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeoutException
import kotlin.random.Random

val Context.config: Config get() = (this.applicationContext as App).conf
fun Context.isPathOnSD(path: String) = config.sdCardPath.isNotEmpty() && path.startsWith(config.sdCardPath)
fun Context.isPathOnOTG(path: String) = config.OTGPath.isNotEmpty() && path.startsWith(config.OTGPath)

fun Context.getSAFOnlyDirs() = DIRS_ACCESSIBLE_ONLY_WITH_SAF.map {"${config.internalStoragePath}$it"} +
	DIRS_ACCESSIBLE_ONLY_WITH_SAF.map {"${config.sdCardPath}$it"}
fun Context.isSAFOnlyRoot(path: String) = getSAFOnlyDirs().any {"${path.trimEnd('/')}/".startsWith(it)}
fun Context.isRestrictedSAFOnlyRoot(path: String) = isRPlus() && isSAFOnlyRoot(path)

var IDCount: UByte = 0u

//Compat w/ Chu ID v1.3 by Pecacheu
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

fun Context.isPathOnRoot(path: String) = !(path.startsWith(config.internalStoragePath) || isPathOnOTG(path) || isPathOnSD(path))
fun isRemotePath(path: String) = path.startsWith(REMOTE_URI)
fun idFromRemotePath(path: String) = path.substring(REMOTE_URI.length, REMOTE_URI.length+UUID.LENGTH)

private fun humanBasePath(ctx: Context, path: String): String {
	return when {
		path == "/" -> ctx.getString(R.string.root)
		path == ctx.config.internalStoragePath -> ctx.getString(R.string.internal)
		path == ctx.config.OTGPath -> ctx.getString(R.string.usb)
		isRemotePath(path) -> ctx.config.getRemoteForPath(path)?.name?:ctx.getString(R.string.unknown)
		else -> ctx.getString(R.string.sd_card)
	}
}

fun Context.humanizePath(path: String): String {
	val trimPath = path.trimEnd('/')
	val basePath = path.getBasePath(this)
	val repPath = humanBasePath(this, basePath)
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

fun Context.formatErr(sid: Int, cause: Throwable?=null, vararg args: Any?) = Error(getString(sid).format(*args), cause)

fun isNotFoundErr(e: Throwable): Boolean {
	var e2 = e
	if(e::class == Error::class) e2 = e.cause?:e //Try to get real error
	return e2 is FileNotFoundException ||
		(e2 is SMBApiException && e2.status == NtStatus.STATUS_OBJECT_NAME_NOT_FOUND)
}

fun Activity.error(e: Throwable, prompt: String?=null, title: String?=null, cb: ((res: Boolean)->Unit)?=null) {
	var ps = prompt
	var fn = cb
	var e2 = e
	if(e is Remote.KeyException) {
		ps = getString(org.fossify.filemanager.R.string.clear_keys)
		fn = {if(it) Remote.clearKeys(this)}
	} else e2 = when(e) { //Common errors
		is UnknownHostException -> formatErr(org.fossify.filemanager.R.string.host_err, e, e.message)
		is SMBApiException -> when(e.status) {
			NtStatus.STATUS_LOGON_FAILURE -> formatErr(org.fossify.filemanager.R.string.login_err, e)
			NtStatus.STATUS_OBJECT_NAME_INVALID -> formatErr(R.string.invalid_name, e)
			NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND ->
				formatErr(org.fossify.filemanager.R.string.not_found, e)
			else -> e
		} else -> e
	}
	var es = if(e2::class == Error::class) e2.message?:getString(R.string.unknown_error_occurred) else e2.toString()
	if(ps != null) es += "\n\n$ps"

	Log.e("files", "Error", e2)
	alert(title?:getString(org.fossify.filemanager.R.string.err_title), es, fn)
}
fun Activity.alert(title: String, msg: String, cb: ((res: Boolean)->Unit)?=null) {
	runOnUiThread {
		val diag = getAlertDialogBuilder().create()
		diag.setTitle(title)
		diag.setMessage(msg)
		val clk = DialogInterface.OnClickListener {dialog, which ->
			diag.dismiss()
			cb?.invoke(which == AlertDialog.BUTTON_POSITIVE)
		}
		diag.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), clk)
		if(cb != null) diag.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel), clk)
		try {diag.show()} catch(_: Throwable) {}
	}
}

private data class AsyncRes<T> (val res: T)

fun <T> blockAsync(timeout: Long=240, fn: (ex: (r: T)->Unit)->Any): T {
	val lock = Object()
	var res: AsyncRes<T>? = null
	var err: Throwable? = null
	val t = Thread {
		try {fn.invoke {synchronized(lock) {res = AsyncRes(it); lock.notifyAll()}}}
		catch(e: Throwable) {synchronized(lock) {err = e; lock.notifyAll()}}
	}
	t.start()
	synchronized(lock) {
		lock.wait(timeout*1000)
		err?.let {throw it}
		res?.let {return it.res}
		throw TimeoutException()
	}
}