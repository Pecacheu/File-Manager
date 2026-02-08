package org.fossify.filemanager.extensions

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.Intent.EXTRA_STREAM
import android.media.RingtoneManager
import android.net.Uri
import android.os.TransactionTooLargeException
import android.util.Log
import org.fossify.commons.R
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.setAsIntent
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.tryGenericMimeType
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.REQUEST_SET_AS
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.filemanager.BuildConfig
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.helpers.OPEN_AS_AUDIO
import org.fossify.filemanager.helpers.OPEN_AS_DEFAULT
import org.fossify.filemanager.helpers.OPEN_AS_IMAGE
import org.fossify.filemanager.helpers.OPEN_AS_TEXT
import org.fossify.filemanager.helpers.OPEN_AS_VIDEO
import org.fossify.filemanager.models.ListItem

//Private in BaseSimpleActivity
private const val GENERIC_PERM_HANDLER = 100

fun Activity.shareUris(uris: ArrayList<Uri>) = getStreamPerms {
	val mimeType = uris.map {it.path!!}.getMimeType()
	val multi = uris.size > 1

	Intent().apply {
		action = if(multi) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
		if(multi) putParcelableArrayListExtra(EXTRA_STREAM, uris)
		else putExtra(EXTRA_STREAM, uris.first())
		type = mimeType
		addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

		try {
			startActivity(Intent.createChooser(this, getString(R.string.share_via)))
		} catch (_: ActivityNotFoundException) {
			toast(R.string.no_app_found)
		} catch(e: RuntimeException) {
			if(e.cause is TransactionTooLargeException) toast(R.string.maximum_share_reached)
			else error(e)
		} catch(e: Throwable) {error(e)}
	}
}

fun Activity.pickedUris(uris: ArrayList<Uri>) = getStreamPerms {
	val mimeType = uris.map {it.path!!}.getMimeType()
	Intent().apply {
		if(uris.size > 1) {
			val cd = ClipData("Attachment", arrayOf(mimeType), ClipData.Item(uris.removeAt(0)))
			for(uri in uris) cd.addItem(ClipData.Item(uri))
			clipData = cd
		} else {
			setDataAndType(uris.first(), mimeType)
		}
		flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
		setResult(RESULT_OK, this)
	}
	finish()
}

fun Activity.pickedRingtone(li: ListItem) = getStreamPerms {
	val uri = li.getUri()
	Intent().apply {
		setDataAndType(uri, li.path.getMimeTypeExt())
		flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
		putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri)
		setResult(RESULT_OK, this)
		finish()
	}
}

fun Activity.launchPath(path: String, forceChooser: Boolean,
		openAs: Int=OPEN_AS_DEFAULT, finishActivity: Boolean=false) {
	val li = ListItem(this as SimpleActivity, path, "", false, 0, 0, 0)
	launchItem(li, forceChooser, openAs, finishActivity)
}
fun Activity.launchItem(li: ListItem, forceChooser: Boolean,
		openAs: Int=OPEN_AS_DEFAULT, finishActivity: Boolean=false) = getStreamPerms {
	val mimeType = getMimeType(openAs)?:li.path.getMimeTypeExt()
	Log.i("test", "MIME TYPE: $mimeType")
	val uri = li.getUri()
	Intent().apply {
		action = Intent.ACTION_VIEW
		setDataAndType(uri, mimeType)
		addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
		putExtra(REAL_FILE_PATH, li.path)

		if(!forceChooser && li.path.endsWith(".apk", true)) launchActivityIntent(this)
		else try {
			startActivity(if(forceChooser) Intent.createChooser(this, getString(R.string.open_with)) else this)
		} catch(_: ActivityNotFoundException) {
			if(!tryGenericMimeType(this, mimeType, uri)) toast(R.string.no_app_found)
		} catch(e: Throwable) {error(e)}
	}
	if(finishActivity) finish()
}

@Suppress("DEPRECATION")
private fun Activity.getStreamPerms(cb: ()->Unit) {
	if(!isTiramisuPlus() || this !is SimpleActivity ||
		hasPermission(PERMISSION_POST_NOTIFICATIONS)) {cb(); return}

	val perm = Manifest.permission.POST_NOTIFICATIONS
	actionOnPermission = {
		if(it) cb()
		else toast(getString(org.fossify.filemanager.R.string.notif_required))
	}
	if(shouldShowRequestPermissionRationale(perm)) {
		alert(getString(R.string.permission_required),
				getString(org.fossify.filemanager.R.string.notif_rationale)) {
			if(it) requestPermissions(arrayOf(perm), GENERIC_PERM_HANDLER)
			else actionOnPermission?.invoke(false)
		}
	} else requestPermissions(arrayOf(perm), GENERIC_PERM_HANDLER)
}

private fun getMimeType(type: Int) = when(type) {
	OPEN_AS_DEFAULT -> null
	OPEN_AS_TEXT -> "text/*"
	OPEN_AS_IMAGE -> "image/*"
	OPEN_AS_AUDIO -> "audio/*"
	OPEN_AS_VIDEO -> "video/*"
	else -> "*/*"
}

fun Activity.setAs(li: ListItem) {
	Intent().apply {
		action = Intent.ACTION_ATTACH_DATA
		setDataAndType(li.getUri(), li.path.getMimeTypeExt())
		addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		val chooser = Intent.createChooser(this, getString(R.string.set_as))

		try {startActivityForResult(chooser, REQUEST_SET_AS)}
		catch (_: ActivityNotFoundException) {toast(R.string.no_app_found)}
		catch(e: Throwable) {error(e)}
	}
	setAsIntent(li.path, BuildConfig.APPLICATION_ID)
}