package org.fossify.filemanager.models

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.R
import org.fossify.commons.dialogs.FileConflictDialog
import org.fossify.commons.extensions.createAndroidSAFDirectory
import org.fossify.commons.extensions.createAndroidSAFDocumentId
import org.fossify.commons.extensions.createAndroidSAFFile
import org.fossify.commons.extensions.createDocumentUriUsingFirstParentTreeUri
import org.fossify.commons.extensions.createSAFFileSdk30
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAndroidSAFUri
import org.fossify.commons.extensions.getAndroidTreeUri
import org.fossify.commons.extensions.getBasePath
import org.fossify.commons.extensions.getDirectChildrenCount
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getFileSize
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getItemSize
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getProperSize
import org.fossify.commons.extensions.getStorageRootIdForAndroidDir
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.isAccessibleWithSAFSdk30
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.storeAndroidTreeUri
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_MERGE
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ExternalStorageProviderHack
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_EXTENSION
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.filemanager.BuildConfig
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.extensions.blockAsync
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.formatErr
import org.fossify.filemanager.extensions.idFromRemotePath
import org.fossify.filemanager.extensions.isPathOnOTG
import org.fossify.filemanager.extensions.isPathOnRoot
import org.fossify.filemanager.extensions.isRemotePath
import org.fossify.filemanager.extensions.isRestrictedSAFOnlyRoot
import org.fossify.filemanager.helpers.Remote
import org.fossify.filemanager.helpers.RemoteProvider
import org.fossify.filemanager.helpers.RootHelpers
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder

const val FILE_AUTH = "${BuildConfig.APPLICATION_ID}.provider"

@Suppress("UNCHECKED_CAST", "LocalVariableName")
data class ListItem(val ctx: SimpleActivity?, val path: String, val name: String, val isDir: Boolean, var children: Int,
		var size: Long, var modified: Long, val isSectionTitle: Boolean=false, val isGridDivider: Boolean=false): Comparable<ListItem> {
	val isRemote = isRemotePath(path) //TODO Might be better to only calc this when needed
	val isHidden: Boolean get() = name.startsWith('.')

	companion object {
		var sorting = 0
		fun sectionTitle(path: String, name: String) = ListItem(null, path, name, false, 0, 0, 0, true, false)
		fun gridDivider() = ListItem(null, "", "", false, 0, 0, 0, false, true)
		fun fromFile(ctx: SimpleActivity, file: File, sortBySize: Boolean, showHidden: Boolean, lastMod: Long?=null): ListItem? {
			val name = file.name
			if(!showHidden && name.startsWith('.')) return null
			val isDir = if(lastMod != null) false else file.isDirectory
			val size = if(isDir) {if(sortBySize) file.getProperSize(showHidden) else 0L} else file.length()
			return ListItem(ctx, file.absolutePath, name, isDir, -1, size, lastMod?:file.lastModified())
		}
		fun fromFdItems(ctx: SimpleActivity, fdItems: ArrayList<FileDirItem>): ArrayList<ListItem> {
			val items = ArrayList<ListItem>()
			for(fd in fdItems) items.add(ListItem(ctx, fd.path, fd.name, fd.isDirectory, fd.children, fd.size, fd.modified))
			return items
		}

		//TODO Make this async load search results, maybe multithreaded?
		fun listDir(ctx: SimpleActivity, path: String, recurse: Boolean,
				search: String?=null, _d: DeviceType?=null, cancel: ()->Boolean): ArrayList<ListItem>? {
			if(cancel.invoke()) return null
			val showHidden = ctx.config.shouldShowHidden()
			val getSize = !recurse && sorting and SORT_BY_SIZE != 0
			val d = DeviceType.fromPath(ctx, path)
			//Ensure recursion doesn't cross devices
			if(_d != null && d != _d) return ArrayList<ListItem>(0)

			val dirAll = try {when(d.type) {
				DeviceType.REMOTE -> ctx.config.getRemoteForPath(path,true)!!.listDir(path, ctx)
				DeviceType.SAF -> getAndroidSAFFileItems(ctx, path, showHidden, getSize, search == null)
				DeviceType.OTG -> getOTGItems(ctx, path, showHidden, getSize, search == null)
				DeviceType.ROOT -> blockAsync {RootHelpers(ctx).getFiles(path) {_, li -> it.invoke(li)}}
				else -> {
					val files = File(path).listFiles()
					if(files == null) throw FileNotFoundException()
					val items = ArrayList<ListItem>(files.size)
					for(f in files) {
						val li = fromFile(ctx, f, getSize, showHidden)
						if(li != null) items.add(li)
					}
					items
				}
			}} catch(e: Throwable) {
				var es = when {
					e::class == Error::class -> e.message
					e is FileNotFoundException -> ctx.getString(org.fossify.filemanager.R.string.not_found)
					recurse -> e.toString()
					else -> null
				}
				if(recurse) es += "\n$path"
				throw if(es != null) Error(es, e) else e
			}
			val dir = (if(search == null) dirAll.clone() else dirAll.filter {it.name.contains(search, true)}) as ArrayList<ListItem>
			if(recurse) for(li in dirAll) if(li.isDir) dir.addAll(listDir(ctx, li.path, true, search, d, cancel)?:return null)
			return dir
		}

		fun mkDir(ctx: SimpleActivity, path: String, mkAll: Boolean=false): Boolean {
			return when {
				isRemotePath(path) -> ctx.config.getRemoteForPath(path,true)!!.mkDir(path, mkAll)
				ctx.isPathOnRoot(path) -> blockAsync {RootHelpers(ctx).createFileFolder(path, false, mkAll, it)}
				ctx.isRestrictedSAFOnlyRoot(path) -> ctx.createAndroidSAFDirectory(path) //TODO Error toasts instead of proper error
				dirExists(ctx,path) -> false
				else -> File(path).let {if(mkAll) it.mkdirs() else it.mkdir()}
			}
		}

		fun pathExists(ctx: SimpleActivity, path: String): Boolean {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path)?.exists(path, 0) == true
			//TODO Root
			return ctx.getDoesFilePathExist(path)
		}
		fun dirExists(ctx: SimpleActivity, path: String): Boolean {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path)?.exists(path, 1) == true
			//TODO Root
			return ctx.getIsPathDirectory(path)
		}
		fun fileExists(ctx: SimpleActivity, path: String): Boolean {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path)?.exists(path, 2) == true
			//TODO Root
			return ctx.getDoesFilePathExist(path) && !ctx.getIsPathDirectory(path)
		}
		fun getInputStream(ctx: SimpleActivity, path: String): InputStream {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path,true)!!.openFile(path, false).inputStream
			//TODO Root
			return ctx.getFileInputStreamSync(path)!!
		}
		fun getOutputStream(ctx: SimpleActivity, path: String): OutputStream {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path,true)!!.openFile(path, true, true).outputStream
			//TODO Root
			return getFileOutputStream(ctx, path, true)
		}
	}

	fun getSignature() = "$path-$modified-$size"
	fun getKey() = ObjectKey(getSignature())
	fun getExt() = if(isDir) name else path.substringAfterLast('.', "")
	fun asFdItem() = FileDirItem(path, name, isDir, children, size, modified)
	fun getBubbleText(context: Context, dateFormat: String? = null, timeFormat: String? = null) = when {
		sorting and SORT_BY_SIZE != 0 -> size.formatSize()
		sorting and SORT_BY_DATE_MODIFIED != 0 -> modified.formatDate(context, dateFormat, timeFormat)
		sorting and SORT_BY_EXTENSION != 0 -> getExt().lowercase()
		else -> name
	}

	override fun compareTo(other: ListItem): Int {
		return if(isDir && !other.isDir) -1
		else if (!isDir && other.isDir) 1
		else {
			var result: Int
			when {
				sorting and SORT_BY_NAME != 0 -> {
					result = if(sorting and SORT_USE_NUMERIC_VALUE != 0)
						AlphanumericComparator().compare(name.normalizeString().lowercase(), other.name.normalizeString().lowercase())
						else name.normalizeString().lowercase().compareTo(other.name.normalizeString().lowercase())
				}
				sorting and SORT_BY_SIZE != 0 -> result = when {
					size == other.size -> 0
					size > other.size -> 1
					else -> -1
				}
				sorting and SORT_BY_DATE_MODIFIED != 0 -> {
					result = when {
						modified == other.modified -> 0
						modified > other.modified -> 1
						else -> -1
					}
				}
				else -> result = getExt().lowercase().compareTo(other.getExt().lowercase())
			}
			if(sorting and SORT_DESCENDING != 0) result *= -1
			result
		}
	}

	fun getChildCount(countHidden: Boolean): Int {
		return when {
			isRemote -> ctx!!.config.getRemoteForPath(path)?.getChildCount(path, countHidden)?:0
			ctx!!.isPathOnOTG(path) -> ctx.getDocumentFile(path)?.listFiles()?.filter {
				if(countHidden) true else !it.name!!.startsWith('.')
			}?.size?:0
			else -> File(path).getDirectChildrenCount(ctx, countHidden)
		}
	}

	private fun otgPublicPath() =
		"${ctx!!.config.OTGTreeUri}/document/${ctx.config.OTGPartition}"+
		"%3A${path.substring(ctx.config.OTGPath.length).replace("/", "%2F")}"

	fun previewPath(): Any {
		if(ctx == null) return ""
		if(path.endsWith(".apk", true)) {
			if(isRemote) return "" //Can't load APK previews on remote
			val info = ctx.packageManager.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)?.applicationInfo
			if(info != null) {
				info.sourceDir = path
				info.publicSourceDir = path
				return info.loadIcon(ctx.packageManager)
			}
		} else if(path.isImageFast() || path.isVideoFast()) {
			if(!isRemote) {
				if(ctx.isRestrictedSAFOnlyRoot(path)) return ctx.getAndroidSAFUri(path)
				if(ctx.isPathOnOTG(path) && ctx.config.OTGTreeUri.isNotEmpty() &&
					ctx.config.OTGPartition.isNotEmpty()) return otgPublicPath()
			}
			return path
		}
		return ""
	}

	fun getUri(): Uri = if(isRemote) RemoteProvider.getUri(path)
		else FileProvider.getUriForFile(ctx!!, FILE_AUTH, File(path))

	fun setHidden(hide: Boolean) {
		if(hide == isHidden) return
		val newName = if(hide) ".$name" else name.substring(1)
		copyMove("${path.getParentPath()}/$newName", false)
	}

	fun copyMove(toPath: String, isCopy: Boolean, multi: Boolean=false) {
		try {
			if(!isCopy && toPath == path) throw ctx!!.formatErr(R.string.source_and_destination_same)
			val e = blockAsync {runCopyMove(toPath, isCopy, multi, it)}
			if(e != null) throw e
			ctx!!.config.moveFavorite(path, toPath)
		} finally {
			if(!multi) ctx?.onConflict = 0
		}
	}

	private fun runCopyMove(dest: String, isCopy: Boolean, multi: Boolean, cb: (e: Throwable?)->Unit) {
		Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
		val sDev = DeviceType.fromPath(ctx!!, path)
		val dDev = DeviceType.fromPath(ctx, dest)
		var doRun: ((Boolean)->Unit)? = null
		var cr = ctx.onConflict

		fun onConflict() {
			if(cr == CONFLICT_SKIP) {cb(null); return}
			if(cr != 0) {cb(ctx.formatErr(R.string.unknown_error_occurred)); return}
			//TODO If they hit cancel, callback propagation stops
			ctx.runOnUiThread {FileConflictDialog(ctx, asFdItem(), multi) {res, all ->
				cr = res
				if(all) ctx.onConflict = res
				if(cr == CONFLICT_SKIP) cb(null)
				else if(cr == CONFLICT_OVERWRITE && dest == path)
					cb(ctx.formatErr(R.string.source_and_destination_same))
				else ensureBackgroundThread {
					Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
					doRun!!(true)
				}
			}}
		}
		fun run(hadConflict: Boolean) {
			try {
				if(cr == CONFLICT_MERGE) throw NotImplementedError() //TODO Handle dir merge
				val remote = if(sDev.type == DeviceType.REMOTE && sDev == dDev)
					ctx.config.getRemoteForPath(path, true)!! else null
				var dPath = dest.trimEnd('/')
				if(remote != null) {
					//val rename = !isCopy && path.getParentPath() == dPath.getParentPath()
					if(/*rename || */!isDir) {
						if(hadConflict && cr == CONFLICT_KEEP_BOTH) dPath = getAltFilename(ctx, dPath)
						/*val res = if(rename) remote.rename(path, dPath, cr == CONFLICT_OVERWRITE) //Remote rename
							else remote.copyFile(path, dPath, cr == CONFLICT_OVERWRITE) //Remote copy
						if(res) cb(null) else onConflict()*/
						if(!remote.copyFile(path, dPath, cr == CONFLICT_OVERWRITE)) onConflict() else {
							if(!isCopy) delete()
							cb(null)
						}
						return
					}
				}
				if(hadConflict || pathExists(ctx, dPath)) { //Conflict
					if(cr == CONFLICT_OVERWRITE) ListItem(ctx, dPath, "", false, 0, 0, 0).delete()
					else if(cr == CONFLICT_KEEP_BOTH) dPath = getAltFilename(ctx, dPath)
					else {onConflict(); return}
				}
				if((sDev.type == DeviceType.ROOT && dDev.type != DeviceType.REMOTE) ||
					(dDev.type == DeviceType.ROOT && sDev.type != DeviceType.REMOTE)) { //Root required
					val res = blockAsync {RootHelpers(ctx).copyMoveFiles(arrayListOf(this), dPath, isCopy, 0, it)}
					if(res < 1) throw ctx.formatErr(R.string.copy_move_failed)
				} else if(!isCopy && sDev.type == DeviceType.DEV && sDev == dDev) { //Both internal
					if(!File(path).renameTo(File(dPath))) throw ctx.formatErr(R.string.copy_move_failed)
				} //TODO Fast rename on OTG / SAF
				else doCopyMove(this, dPath, isCopy, remote) //The long method
				cb(null)
			} catch(e: Throwable) {cb(e)}
		}

		doRun = {run(it)}
		if(dDev.type == DeviceType.REMOTE) run(false)
		else ctx.handleSAFDialog(dest) {
			if(!it) {
				cb(ctx.formatErr(R.string.permission_required))
				return@handleSAFDialog
			}
			ctx.handleSAFDialogSdk30(dest) {
				if(!it) {
					cb(ctx.formatErr(R.string.permission_required))
					return@handleSAFDialogSdk30
				}
				run(false)
			}
		}
	}

	fun delete() {
		ctx!!.config.removeFavorite(path)
		when {
			isRemote -> ctx.config.getRemoteForPath(path,true)!!.delete(path)
			ctx.isPathOnRoot(path) -> RootHelpers(ctx).deleteFiles(arrayListOf(this))
			//TODO Delete isRestrictedSAFOnlyRoot
			else -> {
				if(isDir) {
					val dir = listDir(ctx, path, false) {false}!!
					for(f in dir) f.delete()
				}
				if(!File(path).delete()) throw ctx.formatErr(R.string.unknown_error_occurred)
			}
		}
	}
}

data class DeviceType(val type: Int, val id: String?) {
	companion object {
		const val REMOTE = 1
		const val SAF = 2
		const val OTG = 3
		const val ROOT = 4
		const val DEV = 5

		fun fromPath(ctx: Context, path: String): DeviceType {
			var id: String? = null
			val type = when {
				isRemotePath(path) -> {
					id = idFromRemotePath(path)
					REMOTE
				} ctx.isRestrictedSAFOnlyRoot(path) -> SAF
				ctx.isPathOnOTG(path) -> OTG
				ctx.isPathOnRoot(path) -> ROOT
				else -> DEV
			}
			return DeviceType(type, id)
		}
	}
}

private fun getFileOutputStream(ctx: Context, path: String, reqNew: Boolean): OutputStream {
	val target = File(path)
	val exists = ctx.getDoesFilePathExist(path)
	if(exists && reqNew) throw FileAlreadyExistsException(target)

	val os = when {
		ctx.isRestrictedSAFOnlyRoot(path) -> {
			val uri = ctx.getAndroidSAFUri(path)
			if(!exists) ctx.createAndroidSAFFile(path)
			ctx.contentResolver.openOutputStream(uri, "wt")
		} ctx.isAccessibleWithSAFSdk30(path) -> {
			try {
				if(!exists) ctx.createSAFFileSdk30(path)
				val uri = ctx.createDocumentUriUsingFirstParentTreeUri(path)
				ctx.contentResolver.openOutputStream(uri, "wt")
			} catch(_: Throwable) {null}
		} else -> null
	}
	return os?:FileOutputStream(target)
}

private fun getAndroidSAFFileItems(ctx: SimpleActivity, path: String, showHidden: Boolean,
		getSize: Boolean, getChildCnt: Boolean): ArrayList<ListItem> {
	val rootDocId = ctx.getStorageRootIdForAndroidDir(path)
	val treeUri = ctx.getAndroidTreeUri(path).toUri()
	val newDocId = ctx.createAndroidSAFDocumentId(path)
	val childrenUri = try {
		DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, newDocId)
	} catch(e: Throwable) {
		ctx.storeAndroidTreeUri(path, "")
		throw e
	}
	val proj = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED)
	val rawCursor = ctx.contentResolver.query(childrenUri, proj, null, null)!!
	val cursor = ExternalStorageProviderHack.transformQueryResult(rootDocId, childrenUri, rawCursor)
	val items = ArrayList<ListItem>()
	cursor.use {
		if(cursor.moveToFirst()) {
			do {
				val docId = cursor.getStringValue(Document.COLUMN_DOCUMENT_ID)
				val name = cursor.getStringValue(Document.COLUMN_DISPLAY_NAME)
				val mimeType = cursor.getStringValue(Document.COLUMN_MIME_TYPE)
				val lastMod = cursor.getLongValue(Document.COLUMN_LAST_MODIFIED)
				val isDir = mimeType == Document.MIME_TYPE_DIR
				val filePath = docId.substring("${ctx.getStorageRootIdForAndroidDir(path)}:".length)
				if(!showHidden && name.startsWith('.')) continue
				val decodedPath = path.getBasePath(ctx)+"/"+URLDecoder.decode(filePath, "UTF-8")
				val fileSize = when {
					getSize -> ctx.getFileSize(treeUri, docId)
					isDir -> 0L
					else -> ctx.getFileSize(treeUri, docId)
				}
				val childCount = if(isDir && getChildCnt) ctx.getDirectChildrenCount(rootDocId, treeUri, docId, showHidden) else 0
				items.add(ListItem(ctx, decodedPath, name, isDir, childCount, fileSize, lastMod))
			} while(cursor.moveToNext())
		}
	}
	return items
}

private fun getOTGItems(ctx: SimpleActivity, path: String, showHidden: Boolean,
		getSize: Boolean, getChildCnt: Boolean): ArrayList<ListItem> {
	var rootUri = try {
		DocumentFile.fromTreeUri(ctx.applicationContext, ctx.config.OTGTreeUri.toUri())
	} catch (e: Throwable) {
		ctx.config.OTGPath = ""
		ctx.config.OTGTreeUri = ""
		ctx.config.OTGPartition = ""
		throw e
	}
	val parts = path.split('/').dropLastWhile {it.isEmpty()}
	for(p in parts) {
		if(path == ctx.config.OTGPath) break
		if(p == "otg:" || p == "") continue
		val file = rootUri!!.findFile(p)
		if(file != null) rootUri = file
	}
	val files = rootUri!!.listFiles().filter {it.exists()}
	val basePath = "${ctx.config.OTGTreeUri}/document/${ctx.config.OTGPartition}%3A"
	val items = ArrayList<ListItem>()
	for(f in files) {
		val name = f.name?:continue
		if(!showHidden && name.startsWith('.')) continue
		val isDir = f.isDirectory
		val filePath = f.uri.toString().substring(basePath.length)
		val decodedPath = ctx.config.OTGPath+"/"+URLDecoder.decode(filePath, "UTF-8")
		val fileSize = when {
			getSize -> f.getItemSize(showHidden)
			isDir -> 0L
			else -> f.length()
		}
		val childCount = if(isDir && getChildCnt) f.listFiles().size else 0
		items.add(ListItem(ctx, decodedPath, name, isDir, childCount, fileSize, f.lastModified()))
	}
	return items
}

private val RE_ALT_NAME = Regex("\\(\\d+\\)$")

private fun getAltFilename(ctx: SimpleActivity, dest: String): String {
	var name = dest.getFilenameFromPath()
	val par = dest.substring(0, dest.length-name.length-1)
	val extIdx = name.lastIndexOf('.')
	val ext = if(extIdx != -1) name.substring(extIdx) else ""
	if(extIdx != -1) name = name.substring(0, extIdx)
	val m = RE_ALT_NAME.find(name)
	if(m != null) name = name.substring(0, m.range.first)
	var newDest: String
	var i = 1
	do {
		newDest = "$par/$name($i)$ext"
		i++
	} while(ListItem.pathExists(ctx, newDest))
	return newDest
}

private fun doCopyMove(src: ListItem, dest: String, isCopy: Boolean, remote: Remote?, _dirs: ArrayList<String>?=null) {
	if(src.isDir) {
		var dirs = _dirs?:ArrayList()
		val items = ListItem.listDir(src.ctx!!, src.path, true) {false}!!
		val pLen = src.path.trimEnd('/').length+1
		for(li in items) {
			val newDest = "$dest/${li.path.substring(pLen)}"
			if(li.isDir) {
				if(!dirs.contains(newDest)) {
					ListItem.mkDir(src.ctx, newDest, true)
					dirs.add(newDest)
				}
			} else {
				val newPar = newDest.getParentPath()
				if(!dirs.contains(newPar)) {
					ListItem.mkDir(src.ctx, newPar, true)
					dirs.add(newPar)
				}
				if(remote != null) remote.copyFile(li.path, newDest, false)
				else doCopyMove(li, newDest, true, null, dirs)
			}
		}
	} else {
		ListItem.getInputStream(src.ctx!!, src.path).use {iStr ->
			ListItem.getOutputStream(src.ctx, dest).use {oStr ->
				iStr.copyTo(oStr, DEFAULT_BUFFER_SIZE)
			}
		}
	}
	if(!isCopy) src.delete()
}