package org.fossify.filemanager.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.viewbinding.ViewBinding
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyFloatingActionButton
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.ItemsFragmentBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.isPathOnRoot
import org.fossify.filemanager.extensions.tryOpenPathIntent
import org.fossify.filemanager.helpers.MAX_COLUMN_COUNT
import org.fossify.filemanager.helpers.RootHelpers

abstract class MyViewPagerFragment<BINDING: MyViewPagerFragment.InnerBinding>(context: Context, attributeSet: AttributeSet):
	RelativeLayout(context, attributeSet) {
	protected var activity: SimpleActivity? = null
	protected var currentViewType = VIEW_TYPE_LIST

	var currentPath = ""
	var lastSearchedText = ""
	var isGetContentIntent = false
	var isGetRingtonePicker = false
	var isPickMultipleIntent = false
	var wantedMimeTypes = listOf("")
	protected var isCreateDocumentIntent = false
	protected lateinit var innerBinding: BINDING
	protected var zoomListener: MyRecyclerView.MyZoomListener? = null

	protected fun clickedPath(path: String) {
		if(isGetContentIntent || isCreateDocumentIntent) {
			(activity as MainActivity).pickedPath(path)
		} else if(isGetRingtonePicker) {
			if(path.isAudioFast()) (activity as MainActivity).pickedRingtone(path)
			else activity?.toast(R.string.select_audio_file)
		} else {
			activity?.tryOpenPathIntent(path, false)
		}
	}

	fun updateIsCreateDocumentIntent(isCreateDocumentIntent: Boolean) {
		val iconId = if(isCreateDocumentIntent) org.fossify.commons.R.drawable.ic_check_vector
		else org.fossify.commons.R.drawable.ic_plus_vector

		this.isCreateDocumentIntent = isCreateDocumentIntent
		val fabIcon = context.resources.getColoredDrawableWithColor(iconId, context.getProperPrimaryColor().getContrastColor())
		innerBinding.itemsFab?.setImageDrawable(fabIcon)
	}

	fun handleFileDeleting(files: ArrayList<FileDirItem>, hasFolder: Boolean) {
		val firstPath = files.firstOrNull()?.path
		if(firstPath.isNullOrEmpty() || context == null) return

		if(context!!.isPathOnRoot(firstPath)) RootHelpers(activity!!).deleteFiles(files)
		else (activity as SimpleActivity).deleteFiles(files, hasFolder) {
			if(!it) activity!!.runOnUiThread {activity!!.toast(org.fossify.commons.R.string.unknown_error_occurred)}
		}
	}

	protected fun isProperMimeType(wantedMimeType: String, path: String, isDirectory: Boolean): Boolean {
		return if(wantedMimeType.isEmpty() || wantedMimeType == "*/*" || isDirectory) {
			true
		} else {
			val fileMimeType = path.getMimeType()
			if(wantedMimeType.endsWith("/*")) {
				fileMimeType.substringBefore("/").equals(wantedMimeType.substringBefore("/"), true)
			} else {
				fileMimeType.equals(wantedMimeType, true)
			}
		}
	}

	protected fun incColCount(by: Int) {
		if(currentViewType == VIEW_TYPE_GRID) {
			context!!.config.fileColumnCnt += by
			(activity as? MainActivity)?.updateFragmentColumnCounts()
		}
	}

	protected fun initZoomListener(layoutManager: MyGridLayoutManager) {
		if(currentViewType == VIEW_TYPE_GRID) {
			zoomListener = object: MyRecyclerView.MyZoomListener {
				override fun zoomIn() {
					if(layoutManager.spanCount > 1) {
						incColCount(-1)
						getRecyclerAdapter()?.finishActMode()
					}
				}
				override fun zoomOut() {
					if(layoutManager.spanCount < MAX_COLUMN_COUNT) {
						incColCount(1)
						getRecyclerAdapter()?.finishActMode()
					}
				}
			}
		} else zoomListener = null
	}

	abstract fun setupFragment(activity: SimpleActivity)
	abstract fun onResume(textColor: Int)
	abstract fun refreshFragment()
	abstract fun searchQueryChanged(text: String)
	abstract fun getRecyclerAdapter(): ItemsAdapter?
	open fun selectAll() {}

	interface InnerBinding {val itemsFab: MyFloatingActionButton?}
	open class BaseInnerBinding(val binding: ViewBinding): InnerBinding {override val itemsFab: MyFloatingActionButton? = null}
	class ItemsInnerBinding(val binding: ItemsFragmentBinding): InnerBinding {override val itemsFab: MyFloatingActionButton = binding.itemsFab}
}