package org.fossify.filemanager.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.databinding.DialogCreateNewBinding
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.readablePath
import org.fossify.filemanager.models.ListItem

class CreateNewItemDialog(val activity: SimpleActivity, val path: String,
		val mkdirOnly: Boolean=false, val callback: (newPath: String)->Unit) {
	private val binding = DialogCreateNewBinding.inflate(activity.layoutInflater)

	init {
		activity.getAlertDialogBuilder().apply {
			if(mkdirOnly) binding.dialogRadioGroup.beGone()
			setPositiveButton(org.fossify.commons.R.string.ok, null)
			setNegativeButton(org.fossify.commons.R.string.cancel, null)
			val title = if(mkdirOnly) org.fossify.commons.R.string.create_new_folder
				else org.fossify.commons.R.string.create_new
			activity.setupDialogStuff(binding.root, this, title) {alertDialog ->
				alertDialog.showKeyboard(binding.itemTitle)
				alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
					val name = binding.itemTitle.value
					if(name.isEmpty()) {
						activity.toast(org.fossify.commons.R.string.empty_name)
					} else if(name.isAValidFilename()) {
						ensureBackgroundThread {
							val newPath = "$path/$name"
							if(createPath(newPath, mkdirOnly || binding.dialogRadioGroup
									.checkedRadioButtonId == R.id.dialog_radio_directory)) {
								activity.runOnUiThread {
									alertDialog.dismiss()
									callback(newPath)
								}
							}
						}
					} else activity.toast(org.fossify.commons.R.string.invalid_name)
				}
			}
		}
	}

	private fun createPath(path: String, dir: Boolean): Boolean {
		var err: Throwable? = null
		try {
			if(dir) {if(ListItem.mkDir(activity, path)) return true}
			else {
				ListItem.getOutputStream(activity, path).close()
				return true
			}
		} catch(e: Throwable) {err = e}

		if(err == null) {
			activity.toast(org.fossify.commons.R.string.name_taken)
		} else {
			val eRes = if(dir) org.fossify.commons.R.string.could_not_create_folder
				else org.fossify.commons.R.string.could_not_create_file
			activity.error(err, null, activity.getString(eRes, activity.readablePath(path)))
		}
		return false
	}
}