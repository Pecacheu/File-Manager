package org.fossify.filemanager.dialogs

import android.app.Dialog
import android.os.Environment
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.exifinterface.media.ExifInterface
import org.fossify.commons.R
import org.fossify.commons.dialogs.BasePropertiesDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.views.MyTextView
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.readablePath
import org.fossify.filemanager.extensions.isRemotePath
import org.fossify.filemanager.models.DeviceType
import org.fossify.filemanager.models.ListItem
import org.fossify.filemanager.models.copyToInter
import org.fossify.filemanager.models.runFileJob
import java.io.InputStream
import java.util.Date
import kotlin.concurrent.fixedRateTimer
import kotlin.io.path.createTempFile

class PropertiesDialog: BasePropertiesDialog {
	private var countHidden = false
	private var isOneItem = false
	private var dialog: Dialog? = null
	private var hashStream: InputStream? = null

	/**
	 * A File Properties dialog constructor with an optional parameter, usable at 1 file selected
	 *
	 * @param activity request activity to avoid some Theme.AppCompat issues
	 * @param items file items
	 */
	constructor(activity: SimpleActivity, items: List<ListItem>): super(activity) {
		countHidden = activity.config.shouldShowHidden()
		isOneItem = items.size == 1

		if(isOneItem) addProperties(items.first())
		else {
			addProperty(R.string.items_selected, items.size.toString())
			if(isSameParent(items)) addProperty(R.string.path, mActivity.readablePath(items[0].path.getParentPath()))
			addProperty(R.string.size, "…", R.id.properties_size)
			addProperty(R.string.files_count, "…", R.id.properties_file_count)

			ensureBackgroundThread {
				var fileCount = 0
				var size = 0L
				items.forEach {
					val info = getSizeAndCount(it)
					if(info.first == -1L) size = -1 else size += info.first
					if(info.second == -1) fileCount = -1 else fileCount += info.second
				}
				val fcStr = if(fileCount >= 0) fileCount.toString() else null
				activity.runOnUiThread {
					setProp(R.id.properties_size, size.formatSize())
					setProp(R.id.properties_file_count, fcStr)
				}
			}
		}

		val builder = activity.getAlertDialogBuilder().setPositiveButton(R.string.ok, null)

		if(items.any {it.path.canModifyEXIF()}) {
			val remExif = if(isRPlus()) Environment.isExternalStorageManager()
				else activity.hasPermission(PERMISSION_WRITE_STORAGE)
			if(remExif) builder.setNeutralButton(R.string.remove_exif, null)
		}

		builder.apply {
			mActivity.setupDialogStuff(mDialogView.root, this, R.string.properties) {diag ->
				diag.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {removeEXIF(items)}
				dialog = diag
			}
		}
	}

	private fun addProperties(item: ListItem) {
		addProperty(R.string.name, item.name)
		addProperty(R.string.path, mActivity.readablePath(item.path.getParentPath()))
		addProperty(R.string.size, "…", R.id.properties_size)

		val fd = item.asFdItem()
		when {
			item.isDir -> {
				addProperty(R.string.direct_children_count, "…", R.id.properties_direct_children_count)
				addProperty(R.string.files_count, "…", R.id.properties_file_count)
			} item.isRemote -> {}
			//TODO Do these on remotes somehow
			item.path.isImageSlow() -> {
				fd.getResolution(mActivity)?.let {addProperty(R.string.resolution, it.formatAsResolution())}
			} item.path.isAudioSlow() -> {
				fd.getDuration(mActivity)?.let {addProperty(R.string.duration, it)}
				fd.getTitle(mActivity)?.let {addProperty(R.string.song_title, it)}
				fd.getArtist(mActivity)?.let {addProperty(R.string.artist, it)}
				fd.getAlbum(mActivity)?.let {addProperty(R.string.album, it)}
			} item.path.isVideoSlow() -> {
				fd.getDuration(mActivity)?.let {addProperty(R.string.duration, it)}
				fd.getResolution(mActivity)?.let {addProperty(R.string.resolution, it.formatAsResolution())}
				fd.getArtist(mActivity)?.let {addProperty(R.string.artist, it)}
				fd.getAlbum(mActivity)?.let {addProperty(R.string.album, it)}
			}
		}

		addProperty(R.string.date_created, "…", R.id.properties_last_modified)
		addProperty(R.string.last_modified, item.modified.formatDate(mActivity))

		ensureBackgroundThread {try {
			val info = getSizeAndCount(item)
			val cTime = item.getCreationTime()
			mActivity.runOnUiThread {
				setProp(R.id.properties_size, info.first.formatSize())
				setProp(R.id.properties_last_modified, cTime.formatDate(mActivity))
			}

			if(item.isDir) {
				var childCount = item.children
				if(childCount <= 0) childCount = item.getChildCount(countHidden)
				val fcStr = if(info.second >= 0) info.second.toString() else null

				mActivity.runOnUiThread {
					setProp(R.id.properties_direct_children_count, childCount.toString())
					setProp(R.id.properties_file_count, fcStr)
				}
			} else {
				ListItem.getInputStream(mActivity as SimpleActivity, item.path).use {
					val exif = ExifInterface(it)
					val dateTaken = exif.getExifDateTaken(mActivity)
					val cameraModel = exif.getExifCameraModel()
					val exifString = exif.getExifProperties()
					val latLon = exif.getLatLong()
					val alt = exif.getAltitude(.0)

					mActivity.runOnUiThread {
						if(dateTaken.isNotEmpty()) addProperty(R.string.date_taken, dateTaken)
						if(cameraModel.isNotEmpty()) addProperty(R.string.camera, cameraModel)
						if(exifString.isNotEmpty()) addProperty(R.string.exif, exifString)
						if(latLon != null) addProperty(R.string.gps_coordinates, "${latLon[0]}, ${latLon[1]}")
						if(alt != .0) addProperty(R.string.altitude, "${alt}m")
					}
				}

				mActivity.runOnUiThread {
					addProperty(R.string.md5, "…", R.id.properties_md5)
					addProperty(R.string.sha1, "…", R.id.properties_sha1)
					addProperty(R.string.sha256, "…", R.id.properties_sha256)

					Thread {
						try {
							calcHash(item, R.id.properties_md5) {it.md5()}
							calcHash(item, R.id.properties_sha1) {it.sha1()}
							calcHash(item, R.id.properties_sha256) {it.sha256()}
						} catch(_: Throwable) {}
					}.start()

					//Await hashes to finish
					fixedRateTimer(startAt = Date(), period = 100) {
						if(dialog?.isShowing != true) {
							hashStream?.close()
							this.cancel()
						}
					}
				}
			}
		} catch(e: Throwable) {mActivity.error(e)}}
	}

	/**
	 * Calculate and display hash as a property in the dialog box
	 * @param item File item to check
	 * @param propId Technical property, e.g. R.id.properties_md5
	 * @param doHash Pass the result given an InputStream, e.g. {stream -> stream.md5()}
	 */
	private fun calcHash(item: ListItem, propId: Int, doHash: (InputStream)->String?) {
		if(dialog?.isShowing != true) return
		hashStream = ListItem.getInputStream(mActivity as SimpleActivity, item.path)
		val digest = hashStream?.use {doHash(it)}
		mActivity.runOnUiThread {setProp(propId, digest)}
	}

	private fun removeEXIF(items: List<ListItem>) {
		ConfirmationDialog(mActivity, "", R.string.remove_exif_confirmation) {
			runFileJob(mActivity, mActivity.getString(R.string.remove_exif), items.any {isRemotePath(it.path)}) {cancel ->
				try {
					for(li in items.filter {!it.isDir && it.path.canModifyEXIF()}) {
						var path = li.path
						val dev = DeviceType.fromPath(mActivity, path)
						if(dev.type != DeviceType.DEV) {
							path = createTempFile().toString()
							ListItem.getOutputStream(mActivity as SimpleActivity, path).use {tmp ->
								ListItem.getInputStream(mActivity as SimpleActivity, li.path).use {
									it.copyToInter(tmp, cancel)
								}
							}
						}
						ExifInterface(path).removeValues()
						if(dev.type != DeviceType.DEV) {
							ListItem.getOutputStream(mActivity as SimpleActivity, li.path).use {
								ListItem.getInputStream(mActivity as SimpleActivity, path).use {tmp ->
									tmp.copyToInter(it, cancel)
								}
							}
						}
					}
					mActivity.toast(R.string.exif_removed)
				} catch(e: Throwable) {mActivity.error(e)}
			}
		}
	}

	private fun setProp(propId: Int, text: String?) {
		val prop = mDialogView.propertiesHolder.findViewById<LinearLayout>(propId)
		if(text != null) prop.findViewById<MyTextView>(R.id.property_value).text = text
		else prop.beGone()
	}

	private fun isSameParent(items: List<ListItem>): Boolean {
		val parent = items[0].path.getParentPath()
		return items.all {it.path.getParentPath() == parent}
	}

	private fun getSizeAndCount(item: ListItem): Pair<Long, Int> {
		var size = item.size
		var count = 0
		//Somehow this actually runs (much) faster than getProperSize and getProperFileCount
		if(item.isDir) {
			size = 0
			val sub = ListItem.listDir(mActivity as SimpleActivity, item.path, true) {dialog?.isShowing != true}
			if(sub == null) return Pair(-1, -1)
			for(li in sub) {
				if(li.size > 0) size += li.size
				++count
			}
		} else count = 1
		return Pair(size, count)
	}
}