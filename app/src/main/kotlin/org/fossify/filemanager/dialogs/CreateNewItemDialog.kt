package org.fossify.filemanager.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.isRPlus
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.databinding.DialogCreateNewBinding
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.helpers.RootHelpers
import java.io.File
import java.io.IOException

class CreateNewItemDialog(val activity: SimpleActivity, val path: String, val callback: (success: Boolean)->Unit) {
	private val binding = DialogCreateNewBinding.inflate(activity.layoutInflater)

	init {
		activity.getAlertDialogBuilder()
			.setPositiveButton(org.fossify.commons.R.string.ok, null)
			.setNegativeButton(org.fossify.commons.R.string.cancel, null)
			.apply {
				activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.create_new) {alertDialog ->
					alertDialog.showKeyboard(binding.itemTitle)
					alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
						val name = binding.itemTitle.value
						if(name.isEmpty()) {
							activity.toast(org.fossify.commons.R.string.empty_name)
						} else if(name.isAValidFilename()) {
							val newPath = "$path/$name"
							if(activity.getDoesFilePathExist(newPath)) {
								activity.toast(org.fossify.commons.R.string.name_taken)
								return@OnClickListener
							}

							if(binding.dialogRadioGroup.checkedRadioButtonId == R.id.dialog_radio_directory) {
								createDirectory(newPath, alertDialog) {
									callback(it)
								}
							} else {
								createFile(newPath, alertDialog) {
									callback(it)
								}
							}
						} else {
							activity.toast(org.fossify.commons.R.string.invalid_name)
						}
					})
				}
			}
	}

	private fun createDirectory(path: String, alertDialog: AlertDialog, callback: (Boolean)->Unit) {
		when {
			activity.needsStupidWritePermissions(path) -> activity.handleSAFDialog(path) {
				if(!it) {
					return@handleSAFDialog
				}
				val documentFile = activity.getDocumentFile(path.getParentPath())
				if(documentFile == null) {
					val e = String.format(activity.getString(org.fossify.commons.R.string.could_not_create_folder), path)
					activity.error(Error(e))
					callback(false)
					return@handleSAFDialog
				}
				documentFile.createDirectory(path.getFilenameFromPath())
				success(alertDialog)
			}

			isRPlus() || path.startsWith(activity.internalStoragePath, true) -> {
				if(activity.isRestrictedSAFOnlyRoot(path)) {
					activity.handleAndroidSAFDialog(path) {
						if(!it) {
							callback(false)
							return@handleAndroidSAFDialog
						}
						if(activity.createAndroidSAFDirectory(path)) {
							success(alertDialog)
						} else {
							val e = String.format(activity.getString(org.fossify.commons.R.string.could_not_create_folder), path)
							activity.error(Error(e))
							callback(false)
						}
					}
				} else {
					if(File(path).mkdirs()) {
						success(alertDialog)
					}
				}
			}

			else -> {
				RootHelpers(activity).createFileFolder(path, false) {
					if(it) {
						success(alertDialog)
					} else {
						callback(false)
					}
				}
			}
		}
	}

	private fun createFile(path: String, alertDialog: AlertDialog, callback: (Boolean)->Unit) {
		try {
			when {
				activity.isRestrictedSAFOnlyRoot(path) -> {
					activity.handleAndroidSAFDialog(path) {
						if(!it) {
							callback(false)
							return@handleAndroidSAFDialog
						}
						if(activity.createAndroidSAFFile(path)) {
							success(alertDialog)
						} else {
							val e = String.format(activity.getString(org.fossify.commons.R.string.could_not_create_file), path)
							activity.error(Error(e))
							callback(false)
						}
					}
				}

				activity.needsStupidWritePermissions(path) -> {
					activity.handleSAFDialog(path) {
						if(!it) {
							return@handleSAFDialog
						}

						val documentFile = activity.getDocumentFile(path.getParentPath())
						if(documentFile == null) {
							val e = String.format(activity.getString(org.fossify.commons.R.string.could_not_create_file), path)
							activity.error(Error(e))
							callback(false)
							return@handleSAFDialog
						}
						documentFile.createFile(path.getMimeType(), path.getFilenameFromPath())
						success(alertDialog)
					}
				}

				isRPlus() || path.startsWith(activity.internalStoragePath, true) -> {
					if(File(path).createNewFile()) {
						success(alertDialog)
					}
				}

				else -> {
					RootHelpers(activity).createFileFolder(path, true) {
						if(it) {
							success(alertDialog)
						} else {
							callback(false)
						}
					}
				}
			}
		} catch(e: IOException) {
			activity.error(e)
			callback(false)
		}
	}

	private fun success(alertDialog: AlertDialog) {
		alertDialog.dismiss()
		callback(true)
	}
}