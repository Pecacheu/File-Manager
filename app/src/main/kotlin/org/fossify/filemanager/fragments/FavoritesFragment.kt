package org.fossify.filemanager.fragments

import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.FavoritesFragmentBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.readablePath
import org.fossify.filemanager.models.ListItem

class FavoritesFragment(context: Context, attributeSet: AttributeSet): MyViewPagerFragment<MyViewPagerFragment.BaseInnerBinding>(context, attributeSet) {
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
				}
				filesIgnoringSearch = favs
				addItems(favs, false)
				if(context != null && currentViewType != viewType) setupLayoutManager(viewType)
			}
		}
	}

	private fun addItems(favs: ArrayList<ListItem>, forceRefresh: Boolean) {
		if(!forceRefresh && favs.hashCode() == recyclerAdapter?.listItems.hashCode()) return
		ItemsAdapter(activity!!, favs, this, binding.favsList, null, false) {
			val main = activity as MainActivity
			main.openPath(it.path)
			main.gotoFilesTab()
			return@ItemsAdapter true
		}.apply {
			setItemListZoom(zoomListener)
			binding.favsList.adapter = this
		}
		if(context.areSystemAnimationsEnabled) {
			binding.favsList.scheduleLayoutAnimation()
		}
	}

	override fun onResume(textColor: Int) {
		recyclerAdapter?.apply {
			updatePrimaryColor()
			updateTextColor(textColor)
			initDrawables()
		}
	}

	private fun setupLayoutManager(viewType: Int) {
		if(viewType == VIEW_TYPE_GRID) setupGridLayoutManager()
		else setupListLayoutManager()
		currentViewType = viewType

		val oldItems = recyclerAdapter?.listItems?.toMutableList() as ArrayList<ListItem>
		binding.favsList.adapter = null
		initZoomListener()
		addItems(oldItems, true)
	}

	private fun setupGridLayoutManager() {
		layoutManager!!.spanCount = context?.config?.fileColumnCnt?:3
	}

	private fun setupListLayoutManager() {
		layoutManager!!.spanCount = 1
	}

	private fun getFavs(viewType: Int, callback: (favs: ArrayList<ListItem>)->Unit) {
		val favorites = activity!!.config.favorites
		val items = ArrayList<ListItem>(favorites.size)
		favorites.forEach {path ->
			var name = activity!!.readablePath(path)
			if(viewType == VIEW_TYPE_GRID) name = name.getFilenameFromPath()
			val itm = ListItem(activity, path, name, true, -2, 0, 0)
			items.add(itm)
		}
		activity?.runOnUiThread {callback(items)}
	}

	override fun getRecyclerView() = binding.favsList

	override fun searchQueryChanged(text: String) {
		val normText = text.normalizeString()
		lastSearchedText = text
		val filtered = filesIgnoringSearch.filter {it.name.normalizeString()
			.contains(normText, true)}.toMutableList() as ArrayList<ListItem>
		recyclerAdapter?.updateItems(filtered, text)
	}
}