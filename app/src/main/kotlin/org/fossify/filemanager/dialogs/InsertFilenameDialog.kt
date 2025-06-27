package org.fossify.filemanager.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.databinding.DialogInsertFilenameBinding
import org.fossify.filemanager.models.ListItem

//TODO Test
class InsertFilenameDialog(val activity: SimpleActivity, var path: String, val callback: (filename: String)->Unit) {
	init {
		val binding = DialogInsertFilenameBinding.inflate(activity.layoutInflater)
		activity.getAlertDialogBuilder().apply {
			setPositiveButton(org.fossify.commons.R.string.ok, null)
			setNegativeButton(org.fossify.commons.R.string.cancel, null)
			activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.filename) {alertDialog ->
				alertDialog.showKeyboard(binding.insertFilenameTitle)
				alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
					var filename = binding.insertFilenameTitle.value
					val ext = binding.insertFilenameExtensionTitle.value

					if(filename.isEmpty()) {
						activity.toast(org.fossify.commons.R.string.filename_cannot_be_empty)
						return@setOnClickListener
					}

					if(ext.isNotEmpty()) filename += ".$ext"
					val path = "$path/$filename"

					if(!filename.isAValidFilename()) {
						activity.toast(org.fossify.commons.R.string.filename_invalid_characters)
						return@setOnClickListener
					}

					ensureBackgroundThread {
						if(ListItem.pathExists(activity, path)) {
							activity.toast(String.format(activity.getString(org.fossify.commons.R.string.file_already_exists), filename))
						} else activity.runOnUiThread {
							callback(filename)
							alertDialog.dismiss()
						}
					}
				}
			}
		}
	}
}