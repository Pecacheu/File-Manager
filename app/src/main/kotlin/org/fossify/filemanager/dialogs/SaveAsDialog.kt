package org.fossify.filemanager.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.extensions.humanizePath
import org.fossify.filemanager.databinding.DialogSaveAsBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.models.ListItem

//TODO Test
class SaveAsDialog(val activity: BaseSimpleActivity, var path: String,
	private val hidePath: Boolean, val callback: (path: String, filename: String)->Unit) {

	init {
		if(path.isEmpty()) path = "${activity.config.getHome("")}/${activity.getCurrentFormattedDateTime()}.txt"

		var name = path.getFilenameFromPath()
		var parPath = path.substring(0, path.length-name.length-1)
		val extIdx = name.lastIndexOf('.')
		val ext = if(extIdx != -1) name.substring(extIdx+1) else ""
		if(extIdx != -1) name = name.substring(0, extIdx)

		val binding = DialogSaveAsBinding.inflate(activity.layoutInflater).apply {
			folderValue.setText(activity.humanizePath(parPath))
			filenameValue.setText(name)
			extensionValue.setText(ext)

			if(hidePath) folderHint.beGone() else {
				folderValue.setOnClickListener {
					FilePickerDialog(activity, parPath, false, false, true, true, showFavoritesButton = true) {
						folderValue.setText(activity.humanizePath(it))
						parPath = it
					}
				}
			}
		}

		activity.getAlertDialogBuilder().apply {
			setPositiveButton(org.fossify.commons.R.string.ok, null)
			setNegativeButton(org.fossify.commons.R.string.cancel, null)
			activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.save_as) {alertDialog ->
				alertDialog.showKeyboard(binding.filenameValue)
				alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
					var filename = binding.filenameValue.value
					val ext = binding.extensionValue.value

					if(filename.isEmpty()) {
						activity.toast(org.fossify.commons.R.string.filename_cannot_be_empty)
						return@setOnClickListener
					}

					if(ext.isNotEmpty()) filename += ".$ext"
					val path = "$parPath/$filename"

					if(!filename.isAValidFilename()) {
						activity.toast(org.fossify.commons.R.string.filename_invalid_characters)
						return@setOnClickListener
					}

					ensureBackgroundThread {
						if(!hidePath && ListItem.pathExists(activity, path)) activity.runOnUiThread {
							val title = String.format(activity.getString(org.fossify.commons.R.string.file_already_exists_overwrite), filename)
							ConfirmationDialog(activity, title) {
								callback(path, filename)
								alertDialog.dismiss()
							}
						} else activity.runOnUiThread {
							callback(path, filename)
							alertDialog.dismiss()
						}
					}
				}
			}
		}
	}
}