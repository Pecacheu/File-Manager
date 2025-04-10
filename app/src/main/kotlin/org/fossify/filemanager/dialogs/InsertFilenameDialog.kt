package org.fossify.filemanager.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.filemanager.databinding.DialogInsertFilenameBinding

class InsertFilenameDialog(val activity: BaseSimpleActivity, var path: String, val callback: (filename: String)->Unit) {
	init {
		val binding = DialogInsertFilenameBinding.inflate(activity.layoutInflater)

		activity.getAlertDialogBuilder()
			.setPositiveButton(org.fossify.commons.R.string.ok, null)
			.setNegativeButton(org.fossify.commons.R.string.cancel, null)
			.apply {
				activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.filename) {alertDialog ->
					alertDialog.showKeyboard(binding.insertFilenameTitle)
					alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
						val filename = binding.insertFilenameTitle.value
						val extension = binding.insertFilenameExtensionTitle.value

						if(filename.isEmpty()) {
							activity.toast(org.fossify.commons.R.string.filename_cannot_be_empty)
							return@setOnClickListener
						}

						var newFilename = filename
						if(extension.isNotEmpty()) {
							newFilename += ".$extension"
						}

						val newPath = "$path/$newFilename"
						if(!newFilename.isAValidFilename()) {
							activity.toast(org.fossify.commons.R.string.filename_invalid_characters)
							return@setOnClickListener
						}

						if(activity.getDoesFilePathExist(newPath)) {
							val msg = String.format(activity.getString(org.fossify.commons.R.string.file_already_exists), newFilename)
							activity.toast(msg)
						} else {
							callback(newFilename)
							alertDialog.dismiss()
						}
					}
				}
			}
	}
}