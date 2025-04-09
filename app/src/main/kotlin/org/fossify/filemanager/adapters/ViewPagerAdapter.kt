package org.fossify.filemanager.adapters

import android.content.Intent
import android.media.RingtoneManager
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_FILES
import org.fossify.commons.helpers.TAB_RECENT_FILES
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.fragments.MyViewPagerFragment

class ViewPagerAdapter(val activity: SimpleActivity, val tabsToShow: ArrayList<Int>): PagerAdapter() {
	override fun instantiateItem(container: ViewGroup, idx: Int): Any {
		val layout = getFragment(idx)
		val view = activity.layoutInflater.inflate(layout, container, false)
		container.addView(view)

		(view as MyViewPagerFragment<*>).apply {
			val isPickRingtoneIntent = activity.intent.action == RingtoneManager.ACTION_RINGTONE_PICKER
			val isGetContentIntent = activity.intent.action == Intent.ACTION_GET_CONTENT || activity.intent.action == Intent.ACTION_PICK
			val isCreateDocumentIntent = activity.intent.action == Intent.ACTION_CREATE_DOCUMENT
			val allowPickingMultipleIntent = activity.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
			val getContentMimeType = if(isGetContentIntent) activity.intent.type?:"" else ""

			val passedExtraMimeTypes = activity.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
			val extraMimeTypes = if(isGetContentIntent && passedExtraMimeTypes != null) passedExtraMimeTypes else null

			this.isGetRingtonePicker = isPickRingtoneIntent
			this.isPickMultipleIntent = allowPickingMultipleIntent
			this.isGetContentIntent = isGetContentIntent
			wantedMimeTypes = extraMimeTypes?.toList()?:listOf(getContentMimeType)
			updateIsCreateDocumentIntent(isCreateDocumentIntent)

			setupFragment(activity)
			onResume(activity.getProperTextColor())
		}

		return view
	}

	override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
		container.removeView(item as View)
	}

	override fun getCount() = tabsToShow.size

	override fun isViewFromObject(view: View, item: Any) = view == item

	private fun getFragment(idx: Int): Int {
		return when(tabsToShow[idx]) {
			TAB_FILES -> R.layout.items_fragment
			TAB_FAVORITES -> R.layout.favorites_fragment
			TAB_RECENT_FILES -> R.layout.recents_fragment
			else -> R.layout.storage_fragment
		}
	}
}