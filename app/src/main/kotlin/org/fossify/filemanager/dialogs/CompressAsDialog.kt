package org.fossify.filemanager.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.extensions.humanizePath
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.databinding.DialogCompressAsBinding
import org.fossify.filemanager.models.ListItem

class CompressAsDialog(val activity: SimpleActivity, val path: String, val callback: (destination: String, password: String?)->Unit) {
	private val binding = DialogCompressAsBinding.inflate(activity.layoutInflater)

	init {
		var name = path.getFilenameFromPath()
		var parPath = path.substring(0, path.length-name.length-1)
		val extIdx = name.lastIndexOf('.')
		if(extIdx != -1) name = name.substring(0, extIdx)

		binding.apply {
			filenameValue.setText(name)
			folder.setText(activity.humanizePath(parPath))
			folder.setOnClickListener {
				FilePickerDialog(activity, parPath) {
					folder.setText(activity.humanizePath(it))
					parPath = it
				}
			}
			passwordProtect.setOnCheckedChangeListener {_, _ ->
				enterPasswordHint.beVisibleIf(passwordProtect.isChecked)
			}
		}

		activity.getAlertDialogBuilder().apply {
			setPositiveButton(org.fossify.commons.R.string.ok, null)
			setNegativeButton(org.fossify.commons.R.string.cancel, null)
			activity.setupDialogStuff(binding.root, this, R.string.compress_as) {alertDialog ->
				alertDialog.showKeyboard(binding.filenameValue)
				alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
					val newName = binding.filenameValue.value
					var password: String? = null
					if(binding.passwordProtect.isChecked) {
						password = binding.password.value
						if(password.isEmpty()) {
							activity.toast(org.fossify.commons.R.string.empty_password_new)
							return@OnClickListener
						}
					}
					when {
						newName.isEmpty() -> activity.toast(org.fossify.commons.R.string.empty_name)
						newName.isAValidFilename() -> {
							val path = "$parPath/$newName.zip"
							ensureBackgroundThread {
								if(ListItem.pathExists(activity, path)) {
									activity.toast(org.fossify.commons.R.string.name_taken)
									return@ensureBackgroundThread
								}
								activity.runOnUiThread {
									alertDialog.dismiss()
									callback(path, password)
								}
							}
						}
						else -> activity.toast(org.fossify.commons.R.string.invalid_name)
					}
				})
			}
		}
	}
}