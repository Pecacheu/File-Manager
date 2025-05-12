package org.fossify.filemanager.extensions

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.Intent.EXTRA_STREAM
import android.net.Uri
import android.os.TransactionTooLargeException
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.setAsIntent
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.tryGenericMimeType
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.REQUEST_SET_AS
import org.fossify.filemanager.BuildConfig
import org.fossify.filemanager.helpers.OPEN_AS_AUDIO
import org.fossify.filemanager.helpers.OPEN_AS_DEFAULT
import org.fossify.filemanager.helpers.OPEN_AS_IMAGE
import org.fossify.filemanager.helpers.OPEN_AS_TEXT
import org.fossify.filemanager.helpers.OPEN_AS_VIDEO
import org.fossify.filemanager.models.ListItem

fun Activity.shareUris(uris: ArrayList<Uri>) {
	var mimeType = uris.map {it.path!!}.getMimeType()
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

fun Activity.chooseUris(uris: ArrayList<Uri>) {
	var mimeType = uris.map {it.path!!}.getMimeType()
	val clipData = ClipData("Attachment", arrayOf(mimeType), ClipData.Item(uris.removeAt(0)))
	for(uri in uris) clipData.addItem(ClipData.Item(uri))

	Intent().apply {
		this.clipData = clipData
		flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
		setResult(RESULT_OK, this)
	}
	finish()
}

fun Activity.launchPath(path: String, forceChooser: Boolean, openAs: Int=OPEN_AS_DEFAULT, finishActivity: Boolean=false) {
	val item = ListItem(this as BaseSimpleActivity, path, "", false, 0, 0, 0)
	launchItem(item, forceChooser, openAs, finishActivity)
}
fun Activity.launchItem(item: ListItem, forceChooser: Boolean, openAs: Int=OPEN_AS_DEFAULT, finishActivity: Boolean=false) {
	val mimeType = getMimeType(openAs)?:item.path.getMimeType()
	val uri = item.getUri()
	Intent().apply {
		action = Intent.ACTION_VIEW
		setDataAndType(uri, mimeType)
		addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
		putExtra(REAL_FILE_PATH, item.path)

		if(!forceChooser && item.path.endsWith(".apk", true)) launchActivityIntent(this)
		else try {
			val chooser = Intent.createChooser(this, getString(R.string.open_with))
			startActivity(if(forceChooser) chooser else this)
		} catch(_: ActivityNotFoundException) {
			if(!tryGenericMimeType(this, mimeType, uri)) toast(R.string.no_app_found)
		} catch(e: Throwable) {error(e)}
	}
	if(finishActivity) finish()
}

private fun getMimeType(type: Int) = when(type) {
	OPEN_AS_DEFAULT -> null
	OPEN_AS_TEXT -> "text/*"
	OPEN_AS_IMAGE -> "image/*"
	OPEN_AS_AUDIO -> "audio/*"
	OPEN_AS_VIDEO -> "video/*"
	else -> "*/*"
}

//TODO Test
fun Activity.setAs(item: ListItem) {
	Intent().apply {
		action = Intent.ACTION_ATTACH_DATA
		setDataAndType(item.getUri(), item.path.getMimeType())
		addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		val chooser = Intent.createChooser(this, getString(R.string.set_as))

		try {startActivityForResult(chooser, REQUEST_SET_AS)}
		catch (_: ActivityNotFoundException) {toast(R.string.no_app_found)}
		catch(e: Throwable) {error(e)}
	}
	setAsIntent(item.path, BuildConfig.APPLICATION_ID)
}