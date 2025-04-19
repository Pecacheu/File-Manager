package org.fossify.filemanager.fragments

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore.Files
import android.provider.MediaStore.Files.FileColumns
import android.util.AttributeSet
import androidx.core.os.bundleOf
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.RecentsFragmentBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.interfaces.ItemOperationsListener
import org.fossify.filemanager.models.ListItem
import java.io.File

private const val RECENTS_LIMIT = 50

class RecentsFragment(context: Context, attributeSet: AttributeSet): MyViewPagerFragment<MyViewPagerFragment.BaseInnerBinding>(context, attributeSet),
	ItemOperationsListener {
	private var filesIgnoringSearch = ArrayList<ListItem>()
	private lateinit var binding: RecentsFragmentBinding

	override fun onFinishInflate() {
		super.onFinishInflate()
		binding = RecentsFragmentBinding.bind(this)
		innerBinding = BaseInnerBinding(binding)
	}

	override fun setupFragment(activity: SimpleActivity) {
		if(this.activity == null) {
			this.activity = activity
			binding.recentsSwipeRefresh.setOnRefreshListener {refreshFragment()}
		}
		refreshFragment()
	}

	override fun refreshFragment() {
		ensureBackgroundThread {
			val viewType = context!!.config.getFolderViewType("")
			getRecents {recents ->
				binding.apply {
					recentsSwipeRefresh.isRefreshing = false
					recentsList.beVisibleIf(recents.isNotEmpty())
					recentsPlaceholder.beVisibleIf(recents.isEmpty())
				}
				filesIgnoringSearch = recents
				addItems(recents, false)
				if(context != null && currentViewType != viewType) setupLayoutManager(viewType)
			}
		}
	}

	private fun addItems(recents: ArrayList<ListItem>, forceRefresh: Boolean) {
		if(!forceRefresh && recents.hashCode() == (binding.recentsList.adapter as? ItemsAdapter)?.listItems.hashCode()) {
			return
		}
		ItemsAdapter(activity as SimpleActivity, recents, this, binding.recentsList, isPickMultipleIntent,
			binding.recentsSwipeRefresh, false) {
			clickedPath((it as FileDirItem).path)
		}.apply {
			setItemListZoom(zoomListener)
			binding.recentsList.adapter = this
		}
		if(context.areSystemAnimationsEnabled) {
			binding.recentsList.scheduleLayoutAnimation()
		}
	}

	override fun onResume(textColor: Int) {
		binding.recentsPlaceholder.setTextColor(textColor)
		getRecyclerAdapter()?.apply {
			updatePrimaryColor()
			updateTextColor(textColor)
			initDrawables()
		}
		binding.recentsSwipeRefresh.isEnabled = lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false
	}

	private fun setupLayoutManager(viewType: Int) {
		if(viewType == VIEW_TYPE_GRID) setupGridLayoutManager()
		else setupListLayoutManager()
		currentViewType = viewType

		val oldItems = (binding.recentsList.adapter as? ItemsAdapter)?.listItems?.toMutableList() as ArrayList<ListItem>
		binding.recentsList.adapter = null
		initZoomListener(binding.recentsList.layoutManager as MyGridLayoutManager)
		addItems(oldItems, true)
	}

	private fun setupGridLayoutManager() {
		val layoutManager = binding.recentsList.layoutManager as MyGridLayoutManager
		layoutManager.spanCount = context?.config?.fileColumnCnt?:3
	}

	private fun setupListLayoutManager() {
		val layoutManager = binding.recentsList.layoutManager as MyGridLayoutManager
		layoutManager.spanCount = 1
	}

	private fun getRecents(callback: (recents: ArrayList<ListItem>)->Unit) {
		val showHidden = context?.config?.shouldShowHidden()?:return
		val listItems = arrayListOf<ListItem>()

		val uri = Files.getContentUri("external")
		val projection = arrayOf(FileColumns.DATA, FileColumns.DISPLAY_NAME, FileColumns.DATE_MODIFIED, FileColumns.SIZE)

		try {
			val queryArgs =
				bundleOf(ContentResolver.QUERY_ARG_LIMIT to RECENTS_LIMIT, ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(FileColumns.DATE_MODIFIED),
					ContentResolver.QUERY_ARG_SORT_DIRECTION to ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)

			context?.contentResolver?.query(uri, projection, queryArgs, null)?.use {cursor ->
				if(cursor.moveToFirst()) {
					do {
						val path = cursor.getStringValue(FileColumns.DATA)
						if(File(path).isDirectory) continue

						val name = cursor.getStringValue(FileColumns.DISPLAY_NAME)?:path.getFilenameFromPath()
						val size = cursor.getLongValue(FileColumns.SIZE)
						val modified = cursor.getLongValue(FileColumns.DATE_MODIFIED)*1000
						val fileDirItem = ListItem(path, name, false, 0, size, modified, false, false)
						if((showHidden || !name.startsWith(".")) && activity?.getDoesFilePathExist(path) == true) {
							if(wantedMimeTypes.any {isProperMimeType(it, path, false)}) listItems.add(fileDirItem)
						}
					} while(cursor.moveToNext())
				}
			}
		} catch(e: Throwable) {
			activity?.error(e)
		}
		activity?.runOnUiThread {callback(listItems)}
	}

	override fun getRecyclerAdapter() = binding.recentsList.adapter as? ItemsAdapter
	override fun toggleFilenameVisibility() {getRecyclerAdapter()?.updateDisplayFilenamesInGrid()}

	override fun columnCountChanged() {
		(binding.recentsList.layoutManager as MyGridLayoutManager).spanCount = context!!.config.fileColumnCnt
		(activity as? MainActivity)?.refreshMenuItems()
		getRecyclerAdapter()?.apply {notifyItemRangeChanged(0, listItems.size)}
	}

	override fun setupFontSize() {getRecyclerAdapter()?.updateFontSizes()}
	override fun setupDateTimeFormat() {getRecyclerAdapter()?.updateDateTimeFormat()}
	override fun selectedPaths(paths: ArrayList<String>) {(activity as MainActivity).pickedPaths(paths)}
	override fun deleteFiles(files: ArrayList<FileDirItem>) {handleFileDeleting(files, false)}

	override fun searchQueryChanged(text: String) {
		lastSearchedText = text
		val filtered = filesIgnoringSearch.filter {it.mName.contains(text, true)}.toMutableList() as ArrayList<ListItem>
		binding.apply {
			(recentsList.adapter as? ItemsAdapter)?.updateItems(filtered, text)
			recentsPlaceholder.beVisibleIf(filtered.isEmpty())
			recentsSwipeRefresh.isEnabled = lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false
		}
	}

	override fun finishActMode() {getRecyclerAdapter()?.finishActMode()}
}