package org.fossify.filemanager.adapters

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.helpers.getFilePlaceholderDrawables
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.databinding.ItemDecompressionListFileDirBinding
import org.fossify.filemanager.models.ListItem
import java.util.Locale

class DecompressItemsAdapter(activity: SimpleActivity, var listItems: MutableList<ListItem>, recyclerView: MyRecyclerView, itemClick: (Any)->Unit):
	MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

	private lateinit var fileDrawable: Drawable
	private lateinit var folderDrawable: Drawable
	private var fileDrawables = HashMap<String, Drawable>()
	private var fontSize = 0f

	init {
		initDrawables()
		fontSize = activity.getTextSize()
	}

	override fun getActionMenuId() = 0
	override fun prepareActionMode(menu: Menu) {}
	override fun actionItemPressed(id: Int) {}
	override fun getSelectableItemCount() = 0
	override fun getIsItemSelectable(position: Int) = false
	override fun getItemSelectionKey(position: Int) = 0
	override fun getItemKeyPosition(key: Int) = 0
	override fun onActionModeCreated() {}
	override fun onActionModeDestroyed() {}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		return createViewHolder(ItemDecompressionListFileDirBinding.inflate(layoutInflater, parent, false).root)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val fileDirItem = listItems[position]
		holder.bindView(fileDirItem, true, false) {itemView, layoutPosition ->
			setupView(itemView, fileDirItem)
		}
		bindViewHolder(holder)
	}

	override fun getItemCount() = listItems.size

	private fun setupView(view: View, item: ListItem) {
		ItemDecompressionListFileDirBinding.bind(view).apply {
			itemName.text = item.name
			itemName.setTextColor(textColor)
			itemName.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
			if(item.isDir) {
				itemIcon.setImageDrawable(folderDrawable)
			} else {
				val drawable = fileDrawables.getOrElse(item.name.substringAfterLast('.').lowercase(Locale.getDefault())) {fileDrawable}
				itemIcon.setImageDrawable(drawable)
			}
		}
	}

	@SuppressLint("UseCompatLoadingForDrawables")
	private fun initDrawables() {
		folderDrawable = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_folder_vector, properPrimaryColor)
		folderDrawable.alpha = 180
		fileDrawable = resources.getDrawable(org.fossify.commons.R.drawable.ic_file_generic, null)
		fileDrawables = getFilePlaceholderDrawables(activity)
	}
}