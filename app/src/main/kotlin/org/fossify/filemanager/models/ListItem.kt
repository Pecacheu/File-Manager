package org.fossify.filemanager.models

import android.content.Context
import org.fossify.commons.extensions.getAndroidSAFDirectChildrenCount
import org.fossify.commons.extensions.getDirectChildrenCount
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.models.FileDirItem
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.isRemotePath
import java.io.File

//isSectionTitle is used only at search results for showing the current folders path
data class ListItem(val mPath: String, val mName: String="", val mIsDirectory: Boolean=false, val mChildren: Int=-1, val mSize: Long=0L,
		val mModified: Long=0L, val isSectionTitle: Boolean, val isGridTypeDivider: Boolean): FileDirItem(mPath, mName, mIsDirectory,
		mChildren, mSize, mModified) {
	fun getChildCount(ctx: Context, countHidden: Boolean): Int {
		return when {
			isRemotePath(path) -> ctx.config.getRemoteForPath(path)?.getChildCount(path, countHidden)?:0
			ctx.isRestrictedSAFOnlyRoot(path) -> ctx.getAndroidSAFDirectChildrenCount(path, countHidden)
			ctx.isPathOnOTG(path) -> ctx.getDocumentFile(path)?.listFiles()?.filter {
				if(countHidden) true else !it.name!!.startsWith(".")
			}?.size?:0
			else -> File(path).getDirectChildrenCount(ctx, countHidden)
		}
	}
}