package org.fossify.filemanager.models

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.createAndroidSAFFile
import org.fossify.commons.extensions.createDirectorySync
import org.fossify.commons.extensions.deleteFile
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAndroidSAFDirectChildrenCount
import org.fossify.commons.extensions.getAndroidSAFFileItems
import org.fossify.commons.extensions.getAndroidSAFUri
import org.fossify.commons.extensions.getDirectChildrenCount
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getFileOutputStreamSync
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getOTGItems
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getProperSize
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.renameFile
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_EXTENSION
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.filemanager.extensions.blockAsync
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.formatErr
import org.fossify.filemanager.extensions.isPathOnRoot
import org.fossify.filemanager.extensions.isRemotePath
import org.fossify.filemanager.helpers.RootHelpers
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class ListItem(val ctx: BaseSimpleActivity?, val path: String, val name: String, val isDir: Boolean, var children: Int,
		var size: Long, var modified: Long, val isSectionTitle: Boolean=false, val isGridDivider: Boolean=false): Comparable<ListItem> {
	val isRemote = isRemotePath(path) //TODO Might be better to only calc this when needed
	val isHidden: Boolean get() = name.startsWith('.')

	companion object {
		var sorting = 0
		fun sectionTitle(path: String, name: String) = ListItem(null, path, name, false, 0, 0, 0, true, false)
		fun gridDivider() = ListItem(null, "", "", false, 0, 0, 0, false, true)
		fun fromFile(ctx: BaseSimpleActivity, file: File, sortBySize: Boolean, showHidden: Boolean, lastMod: Long?=null): ListItem? {
			val name = file.name
			if(!showHidden && name.startsWith('.')) return null
			val isDir = if(lastMod != null) false else file.isDirectory
			val size = if(isDir) {if(sortBySize) file.getProperSize(showHidden) else 0L} else file.length()
			return ListItem(ctx, file.absolutePath, name, isDir, -1, size, lastMod?:file.lastModified())
		}
		fun fromFdItems(ctx: BaseSimpleActivity, fdItems: ArrayList<FileDirItem>): ArrayList<ListItem> {
			val items = ArrayList<ListItem>()
			for(fd in fdItems) items.add(ListItem(ctx, fd.path, fd.name, fd.isDirectory, fd.children, fd.size, fd.modified))
			return items
		}

		//TODO Make this async load search results
		fun listDir(ctx: BaseSimpleActivity, path: String, recurse: Boolean, search: String?=null, cancel: ()->Boolean): ArrayList<ListItem>? {
			if(cancel.invoke()) return null
			val showHidden = ctx.config.shouldShowHidden()
			val sortBySize = search == null && sorting and SORT_BY_SIZE != 0
			val dirAll = when {
				isRemotePath(path) -> {
					ctx.config.getRemoteForPath(path,true)!!.listDir(path, ctx)
				} ctx.isRestrictedSAFOnlyRoot(path) -> {
					val fd = blockAsync {ctx.getAndroidSAFFileItems(path, showHidden, sortBySize, it)}
					fromFdItems(ctx, fd)
				} ctx.isPathOnOTG(path) -> {
					val fd = blockAsync {ctx.getOTGItems(path, showHidden, sortBySize, it)}
					fromFdItems(ctx, fd)
				} ctx.isPathOnRoot(path) -> {
					blockAsync {RootHelpers(ctx).getFiles(path) {path, li -> it.invoke(li)}}
				} else -> {
					val files = File(path).listFiles()
					if(files == null) throw ctx.formatErr(org.fossify.commons.R.string.unknown_error_occurred)
					val items = ArrayList<ListItem>(files.size)
					for(f in files) {
						val li = fromFile(ctx, f, sortBySize, showHidden)
						if(li != null) items.add(li)
					}
					items
				}
			}
			val dir = if(search == null) dirAll else dirAll.filter {it.name.contains(search, true)} as ArrayList<ListItem>
			if(recurse) for(li in dirAll) if(li.isDir) dir.addAll(listDir(ctx, li.path, true, search, cancel)?:return null)
			return dir
		}

		fun mkDir(ctx: BaseSimpleActivity, path: String): Boolean {
			return when {
				isRemotePath(path) -> {
					ctx.config.getRemoteForPath(path,true)!!.mkDir(path)
				} ctx.isPathOnRoot(path) -> {
					blockAsync {RootHelpers(ctx).createFileFolder(path, false, it)}
				} else -> {
					if(pathExists(ctx,path)) false else ctx.createDirectorySync(path)
				}
			}
		}

		//TODO mkFile
		fun mkFile(ctx: BaseSimpleActivity, path: String): Boolean {
			throw NotImplementedError()
			/*
			when {
				activity.isRestrictedSAFOnlyRoot(path) -> {
					activity.handleAndroidSAFDialog(path) {
						if(!it) {
							callback(false)
							return@handleAndroidSAFDialog
						}
						if(activity.createAndroidSAFFile(path)) {
							success(alertDialog)
						} else {
							val e = String.format(activity.getString(org.fossify.commons.R.string.could_not_create_file), path)
							activity.error(Error(e))
							callback(false)
						}
					}
				}
				activity.needsStupidWritePermissions(path) -> {
					activity.handleSAFDialog(path) {
						if(!it) return@handleSAFDialog
						val documentFile = activity.getDocumentFile(path.getParentPath())
						if(documentFile == null) {
							val e = String.format(activity.getString(org.fossify.commons.R.string.could_not_create_file), path)
							activity.error(Error(e))
							callback(false)
							return@handleSAFDialog
						}
						documentFile.createFile(path.getMimeType(), path.getFilenameFromPath())
						success(alertDialog)
					}
				}
				isRPlus() || path.startsWith(activity.internalStoragePath, true) -> {
					if(File(path).createNewFile()) success(alertDialog)
				}
				else -> {
					RootHelpers(activity).createFileFolder(path, true) {
						if(it) success(alertDialog)
						else callback(false)
					}
				}
			}*/
		}

		fun pathExists(ctx: BaseSimpleActivity, path: String): Boolean {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path)?.exists(path, 0) == true
			return ctx.getDoesFilePathExist(path)
		}
		fun dirExists(ctx: BaseSimpleActivity, path: String): Boolean {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path)?.exists(path, 1) == true
			return ctx.getIsPathDirectory(path)
		}
		fun fileExists(ctx: BaseSimpleActivity, path: String): Boolean {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path)?.exists(path, 2) == true
			return ctx.getDoesFilePathExist(path) && !ctx.getIsPathDirectory(path)
		}
		fun getInputStream(ctx: BaseSimpleActivity, path: String): InputStream {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path,true)!!.openFile(path, false).inputStream
			return ctx.getFileInputStreamSync(path)!!
		}
		fun getOutputStream(ctx: BaseSimpleActivity, path: String): OutputStream {
			if(isRemotePath(path)) return ctx.config.getRemoteForPath(path,true)!!.openFile(path, true).outputStream //TODO Test
			return ctx.getFileOutputStreamSync(path, path.getMimeType())!!
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
			isRemotePath(path) -> ctx!!.config.getRemoteForPath(path)?.getChildCount(path, countHidden)?:0
			ctx!!.isRestrictedSAFOnlyRoot(path) -> ctx.getAndroidSAFDirectChildrenCount(path, countHidden)
			ctx.isPathOnOTG(path) -> ctx.getDocumentFile(path)?.listFiles()?.filter {
				if(countHidden) true else !it.name!!.startsWith('.')
			}?.size?:0
			else -> File(path).getDirectChildrenCount(ctx, countHidden)
		}
	}

	private fun otgPublicPath() =
		"${ctx!!.baseConfig.OTGTreeUri}/document/${ctx.baseConfig.OTGPartition}"+
		"%3A${path.substring(ctx.baseConfig.OTGPath.length).replace("/", "%2F")}"

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
				if(ctx.isPathOnOTG(path) && ctx.baseConfig.OTGTreeUri.isNotEmpty() &&
					ctx.baseConfig.OTGPartition.isNotEmpty()) return otgPublicPath()
			}
			return path
		}
		return ""
	}

	fun sharePath(): String {
		if(isRemote) throw NotImplementedError("Remote share") //TODO Remote share
		return path
	}

	fun setHidden(hide: Boolean) {
		if(hide == isHidden) return
		val newName = if(hide) ".$name" else name.substring(1)
		rename("${path.getParentPath()}/$newName")
	}

	fun rename(toPath: String) {
		if(toPath == path) return
		/*if(isRemote && isRemotePath(toPath) && ctx.config.getRemoteForPath(toPath) == ctx.config.getRemoteForPath(path)) {
			//TODO Remote rename
		}*/
		if(isRemote || isRemotePath(toPath)) throw NotImplementedError("Remote rename")
		//TODO Handle root rename, also count total success in ItemsAdapter
		ctx!!.renameFile(path, toPath, false)
		ctx.config.moveFavorite(path, toPath) //TODO Detect fav inside of moved folder recursively
		//activity.checkConflicts
	}

	fun delete() {
		//TODO Detect fav inside of deleted folder recursively
		ctx!!.config.removeFavorite(path)
		when {
			isRemote -> ctx.config.getRemoteForPath(path,true)!!.delete(path)
			ctx.isPathOnRoot(path) -> RootHelpers(ctx).deleteFiles(arrayListOf(this))
			else -> ctx.deleteFile(asFdItem(), isDir) {
				if(!it) ctx.runOnUiThread {ctx.toast(org.fossify.commons.R.string.unknown_error_occurred)}
			}
		}
	}
}