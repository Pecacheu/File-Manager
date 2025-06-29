package org.fossify.filemanager.adapters

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Menu
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.getFilePlaceholderDrawables
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.adapters.ItemsAdapter.Binding
import org.fossify.filemanager.models.ListItem
import java.util.HashMap
import java.util.Locale

class FilepickerItemsAdapter(activity: BaseSimpleActivity, val listItems: List<ListItem>,
	recyclerView: MyRecyclerView, itemClick: (Any)->Unit,
): MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {
	private lateinit var fileDrawable: Drawable
	private lateinit var folderDrawable: Drawable
	private var fileDrawables = HashMap<String, Drawable>()
	private var fontSize = 0f
	private val dateFormat = activity.baseConfig.dateFormat
	private val timeFormat = activity.getTimeFormat()

	init {
		initDrawables()
		fontSize = activity.getTextSize()
	}

	override fun getActionMenuId() = 0

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = Binding.ItemFileDirList.inflate(layoutInflater, parent, false)
		return ViewHolder(binding.root)
	}
	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = listItems[position]
		holder.bindView(item, true, false) {view, pos ->
			setupView(Binding.ItemFileDirList.bind(view), item)
		}
		bindViewHolder(holder)
	}

	override fun getItemCount() = listItems.size
	override fun prepareActionMode(menu: Menu) {}
	override fun actionItemPressed(id: Int) {}
	override fun getSelectableItemCount() = listItems.size
	override fun getIsItemSelectable(position: Int) = false
	override fun getItemKeyPosition(key: Int) = listItems.indexOfFirst {it.path.hashCode() == key}
	override fun getItemSelectionKey(position: Int) = listItems[position].path.hashCode()
	override fun onActionModeCreated() {}
	override fun onActionModeDestroyed() {}

	override fun onViewRecycled(holder: ViewHolder) {
		super.onViewRecycled(holder)
		if(!activity.isDestroyed && !activity.isFinishing) {
			val icon = Binding.ItemFileDirList.bind(holder.itemView).itemIcon
			if(icon != null) Glide.with(activity).clear(icon)
		}
	}

	private fun setupView(binding: ItemsAdapter.ItemViewBinding, item: ListItem) {
		binding.apply {
			root.setupViewBackground(activity)
			itemName?.text = item.name
			itemName?.setTextColor(textColor)
			itemName?.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
			itemDetails?.setTextColor(textColor)
			itemDetails?.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
			itemDate?.beGone()

			if(item.isDir) {
				itemIcon?.setImageDrawable(folderDrawable)
				if(item.children == -2) itemDetails?.beGone()
				else {
					itemDetails?.text = if(item.children == -1) ""
					else activity.resources.getQuantityString(R.plurals.items, item.children, item.children)
					itemDetails?.beVisible()
				}
			} else {
				itemDetails?.text = item.size.formatSize()
				val drawable = fileDrawables.getOrElse(item.name.substringAfterLast('.').lowercase(Locale.getDefault())) {fileDrawable}
				val opts = RequestOptions().signature(item.getKey())
					.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
					.error(drawable)
					.transform(CenterCrop(), RoundedCorners(10))

				val imgPath = item.previewPath()
				if(!activity.isDestroyed && itemIcon != null) {
					Glide.with(activity).load(imgPath)
						.transition(DrawableTransitionOptions.withCrossFade())
						.apply(opts).into(itemIcon!!)
				}
			}
		}
	}

	@SuppressLint("UseCompatLoadingForDrawables")
	private fun initDrawables() {
		folderDrawable = resources.getColoredDrawableWithColor(R.drawable.ic_folder_vector, properPrimaryColor)
		folderDrawable.alpha = 180
		fileDrawable = resources.getDrawable(R.drawable.ic_file_generic, null)
		fileDrawables = getFilePlaceholderDrawables(activity)
	}

	override fun onChange(position: Int) = listItems.getOrNull(position)?.getBubbleText(activity, dateFormat, timeFormat)?:""
}