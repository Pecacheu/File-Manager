package org.fossify.filemanager.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_FILES
import org.fossify.commons.helpers.TAB_RECENT_FILES
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.fragments.MyViewPagerFragment

@SuppressLint("NotifyDataSetChanged")
class ViewPagerAdapter(private val activity: SimpleActivity, private var tabsToShow: ArrayList<Int>):
	RecyclerView.Adapter<ViewPagerAdapter.ViewHolder>() {
	var view: ViewPager2? = null

	override fun onAttachedToRecyclerView(view: RecyclerView) {
		this.view = view.parent as ViewPager2
		setTabs(null)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val holder = ViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))

		val isPickRingtoneIntent = activity.intent.action == RingtoneManager.ACTION_RINGTONE_PICKER
		val isGetContentIntent = activity.intent.action == Intent.ACTION_GET_CONTENT || activity.intent.action == Intent.ACTION_PICK
		val isCreateDocumentIntent = activity.intent.action == Intent.ACTION_CREATE_DOCUMENT
		val allowPickingMultipleIntent = activity.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
		val getContentMimeType = if(isGetContentIntent) activity.intent.type?:"" else ""
		val passedExtraMimeTypes = activity.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
		val extraMimeTypes = if(isGetContentIntent && passedExtraMimeTypes != null) passedExtraMimeTypes else null

		(holder.itemView as MyViewPagerFragment<*>).apply {
			this.isGetRingtonePicker = isPickRingtoneIntent
			this.isPickMultipleIntent = allowPickingMultipleIntent
			this.isGetContentIntent = isGetContentIntent
			wantedMimeTypes = extraMimeTypes?.toList()?:listOf(getContentMimeType)
			updateIsCreateDocumentIntent(isCreateDocumentIntent)
			setupFragment(activity)
			(holder.itemView as MyViewPagerFragment<*>).onResume(activity.getProperTextColor())
		}
		return holder
	}

	override fun onBindViewHolder(holder: ViewHolder, idx: Int) {}
	override fun getItemCount() = tabsToShow.size

	override fun getItemViewType(idx: Int): Int {
		return when(tabsToShow[idx]) {
			TAB_FILES -> R.layout.items_fragment
			TAB_FAVORITES -> R.layout.favorites_fragment
			TAB_RECENT_FILES -> R.layout.recents_fragment
			else -> R.layout.storage_fragment
		}
	}

	fun setTabs(tabs: ArrayList<Int>?) {
		if(tabs != null) tabsToShow = tabs
		(view as ViewPager2).offscreenPageLimit = tabsToShow.size
		notifyDataSetChanged()
	}

	inner class ViewHolder(view: View): RecyclerView.ViewHolder(view)
}