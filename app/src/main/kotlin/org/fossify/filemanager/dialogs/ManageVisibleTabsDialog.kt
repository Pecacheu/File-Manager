package org.fossify.filemanager.dialogs

import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_FILES
import org.fossify.commons.helpers.TAB_RECENT_FILES
import org.fossify.commons.helpers.TAB_STORAGE_ANALYSIS
import org.fossify.commons.views.MyAppCompatCheckbox
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.databinding.DialogManageVisibleTabsBinding
import org.fossify.filemanager.extensions.config

class ManageVisibleTabsDialog(val activity: SimpleActivity) {
	private val binding = DialogManageVisibleTabsBinding.inflate(activity.layoutInflater)
	private val tabs = LinkedHashMap<Int, Int>()

	init {
		tabs.apply {
			put(TAB_FAVORITES, R.id.manage_visible_tabs_favorites)
			put(TAB_RECENT_FILES, R.id.manage_visible_tabs_recent_files)
			put(TAB_STORAGE_ANALYSIS, R.id.manage_visible_tabs_storage_analysis)
		}
		val showTabs = activity.config.showTabs
		for((key, value) in tabs) {
			binding.root.findViewById<MyAppCompatCheckbox>(value).isChecked = showTabs and key != 0
		}
		activity.getAlertDialogBuilder()
			.setPositiveButton(org.fossify.commons.R.string.ok) {dialog, which -> dialogConfirmed()}
			.setNegativeButton(org.fossify.commons.R.string.cancel, null)
			.apply {activity.setupDialogStuff(binding.root, this)}
	}

	private fun dialogConfirmed() {
		var result = TAB_FILES
		for((key, value) in tabs) {
			if(binding.root.findViewById<MyAppCompatCheckbox>(value).isChecked) result += key
		}
		activity.config.showTabs = result
	}
}