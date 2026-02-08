package org.fossify.filemanager.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.viewbinding.ViewBinding
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.views.MyFloatingActionButton
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.ItemsFragmentBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.getMimeTypeExt
import org.fossify.filemanager.helpers.MAX_COLUMN_COUNT
import org.fossify.filemanager.interfaces.ItemOperationsListener

abstract class MyViewPagerFragment<BINDING: MyViewPagerFragment.InnerBinding>(context: Context, attributeSet: AttributeSet):
		RelativeLayout(context, attributeSet), ItemOperationsListener {
	protected var activity: SimpleActivity? = null
	protected var currentViewType = VIEW_TYPE_LIST

	var currentPath = ""
	var lastSearchedText = ""
	var isGetContentIntent = false
	var isGetRingtonePicker = false
	var isPickMultipleIntent = false
	var isCreateDocumentIntent = false
	var wantedMimeTypes = listOf("")
	protected lateinit var innerBinding: BINDING
	protected var zoomListener: MyRecyclerView.MyZoomListener? = null

	fun updateIsCreateDocumentIntent(isCreateDocumentIntent: Boolean) {
		val iconId = if(isCreateDocumentIntent) org.fossify.commons.R.drawable.ic_check_vector
		else org.fossify.commons.R.drawable.ic_plus_vector

		this.isCreateDocumentIntent = isCreateDocumentIntent
		val fabIcon = context.resources.getColoredDrawableWithColor(iconId, context.getProperPrimaryColor().getContrastColor())
		innerBinding.itemsFab?.setImageDrawable(fabIcon)
	}

	protected fun isProperMimeType(wantedMimeType: String, path: String, isDirectory: Boolean): Boolean {
		return if(wantedMimeType.isEmpty() || wantedMimeType == "*/*" || isDirectory) {
			true
		} else {
			val mimeType = path.getMimeTypeExt()
			if(wantedMimeType.endsWith("/*")) mimeType.substringBefore("/").equals(wantedMimeType.substringBefore("/"), true)
				else mimeType.equals(wantedMimeType, true)
		}
	}

	protected fun incColCount(by: Int) {
		if(currentViewType == VIEW_TYPE_GRID) {
			context!!.config.fileColumnCnt += by
			(activity as? MainActivity)?.updateFragmentColumnCounts()
		}
		recyclerAdapter?.finishActMode()
	}

	protected fun initZoomListener() {
		zoomListener = if(currentViewType == VIEW_TYPE_GRID) {
			object: MyRecyclerView.MyZoomListener {
				override fun zoomIn() {
					if(layoutManager!!.spanCount > 1) incColCount(-1)
				}
				override fun zoomOut() {
					if(layoutManager!!.spanCount < MAX_COLUMN_COUNT) incColCount(1)
				}
			}
		} else null
	}

	override fun toggleFilenameVisibility() {recyclerAdapter?.updateDisplayFilenamesInGrid()}
	override fun setupFontSize() {recyclerAdapter?.updateFontSizes()}
	override fun setupDateTimeFormat() {recyclerAdapter?.updateDateTimeFormat()}
	override fun finishActMode() {recyclerAdapter?.finishActMode()}

	fun columnCountChanged() {
		if(currentViewType != VIEW_TYPE_GRID) return
		layoutManager?.spanCount = context!!.config.fileColumnCnt
		recyclerAdapter?.apply {notifyItemRangeChanged(0, listItems.size)}
	}

	val recyclerAdapter
		get() = getRecyclerView()?.adapter as? ItemsAdapter
	val layoutManager
		get() = getRecyclerView()?.layoutManager as? MyGridLayoutManager

	abstract fun setupFragment(activity: SimpleActivity)
	abstract fun onResume(textColor: Int)
	abstract fun searchQueryChanged(text: String)
	abstract fun getRecyclerView(): MyRecyclerView?

	interface InnerBinding {val itemsFab: MyFloatingActionButton?}
	open class BaseInnerBinding(val binding: ViewBinding): InnerBinding {override val itemsFab: MyFloatingActionButton? = null}
	class ItemsInnerBinding(val binding: ItemsFragmentBinding): InnerBinding {override val itemsFab: MyFloatingActionButton = binding.itemsFab}
}