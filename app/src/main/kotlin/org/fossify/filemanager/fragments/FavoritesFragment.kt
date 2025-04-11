package org.fossify.filemanager.fragments

import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.FavoritesFragmentBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.interfaces.ItemOperationsListener
import org.fossify.filemanager.models.ListItem
import java.io.File

class FavoritesFragment(context: Context, attributeSet: AttributeSet): MyViewPagerFragment<MyViewPagerFragment.BaseInnerBinding>(context, attributeSet),
	ItemOperationsListener {
	private var filesIgnoringSearch = ArrayList<ListItem>()
	private lateinit var binding: FavoritesFragmentBinding

	override fun onFinishInflate() {
		super.onFinishInflate()
		binding = FavoritesFragmentBinding.bind(this)
		innerBinding = BaseInnerBinding(binding)
	}

	override fun setupFragment(activity: SimpleActivity) {
		if(this.activity == null) this.activity = activity
		refreshFragment()
	}

	override fun refreshFragment() {
		ensureBackgroundThread {
			val viewType = context!!.config.getFolderViewType("")
			getFavs(viewType) {favs ->
				binding.apply {
					favsList.beVisibleIf(favs.isNotEmpty())
					favsPlaceholder.beVisibleIf(favs.isEmpty())
				}
				filesIgnoringSearch = favs
				addItems(favs, false)
				if(context != null && currentViewType != viewType) setupLayoutManager(viewType)
			}
		}
	}

	private fun addItems(favs: ArrayList<ListItem>, forceRefresh: Boolean) {
		if(!forceRefresh && favs.hashCode() == (binding.favsList.adapter as? ItemsAdapter)?.listItems.hashCode()) {
			return
		}
		ItemsAdapter(activity as SimpleActivity, favs, this, binding.favsList, isPickMultipleIntent,
			null, false) {
			val main = activity as MainActivity
			main.openPath((it as FileDirItem).path)
			main.gotoFilesTab()
		}.apply {
			setItemListZoom(zoomListener)
			binding.favsList.adapter = this
		}
		if(context.areSystemAnimationsEnabled) {
			binding.favsList.scheduleLayoutAnimation()
		}
	}

	override fun onResume(textColor: Int) {
		binding.favsPlaceholder.setTextColor(textColor)
		getRecyclerAdapter()?.apply {
			updatePrimaryColor()
			updateTextColor(textColor)
			initDrawables()
		}
	}

	private fun setupLayoutManager(viewType: Int) {
		if(viewType == VIEW_TYPE_GRID) setupGridLayoutManager()
		else setupListLayoutManager()
		currentViewType = viewType

		val oldItems = (binding.favsList.adapter as? ItemsAdapter)?.listItems?.toMutableList() as ArrayList<ListItem>
		binding.favsList.adapter = null
		initZoomListener(binding.favsList.layoutManager as MyGridLayoutManager)
		addItems(oldItems, true)
	}

	private fun setupGridLayoutManager() {
		val layoutManager = binding.favsList.layoutManager as MyGridLayoutManager
		layoutManager.spanCount = context?.config?.fileColumnCnt?:3
	}

	private fun setupListLayoutManager() {
		val layoutManager = binding.favsList.layoutManager as MyGridLayoutManager
		layoutManager.spanCount = 1
	}

	private fun getFavs(viewType: Int, callback: (favs: ArrayList<ListItem>)->Unit) {
		val favorites = activity!!.config.favorites
		val items = ArrayList<ListItem>(favorites.size)
		favorites.forEach {path ->
			val name = if(viewType == VIEW_TYPE_GRID) path.getFilenameFromPath() else activity!!.humanizePath(path)
			val itm = ListItem(path, name, File(path).isDirectory, 0,
				0, 0, false, false)
			items.add(itm)
		}
		activity?.runOnUiThread {callback(items)}
	}

	override fun getRecyclerAdapter() = binding.favsList.adapter as? ItemsAdapter

	override fun toggleFilenameVisibility() {
		getRecyclerAdapter()?.updateDisplayFilenamesInGrid()
	}

	override fun columnCountChanged() {
		(binding.favsList.layoutManager as MyGridLayoutManager).spanCount = context!!.config.fileColumnCnt
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
			(favsList.adapter as? ItemsAdapter)?.updateItems(filtered, text)
			favsPlaceholder.beVisibleIf(filtered.isEmpty())
		}
	}

	override fun finishActMode() {getRecyclerAdapter()?.finishActMode()}
}