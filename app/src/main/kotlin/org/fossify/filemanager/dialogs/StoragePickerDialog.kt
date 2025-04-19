package org.fossify.filemanager.dialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.databinding.DialogRadioGroupBinding
import org.fossify.commons.databinding.RadioButtonBinding
import org.fossify.commons.extensions.*
import org.fossify.filemanager.extensions.getBasePath
import org.fossify.filemanager.extensions.config

class StoragePickerDialog(val activity: BaseSimpleActivity, val path: String,
		showRoot: Boolean, val callback: (pickedPath: String)->Unit) {
	private lateinit var radioGroup: RadioGroup
	private var dialog: AlertDialog? = null
	private val storages = LinkedHashMap<String, String>()
	private var defaultId = 0

	init {
		storages.put(activity.getString(R.string.internal), activity.internalStoragePath)
		if(activity.hasExternalSDCard()) storages.put(activity.getString(R.string.sd_card), activity.sdCardPath)
		if(activity.hasOTGConnected()) storages.put(activity.getString(R.string.usb), activity.otgPath)
		if(showRoot) storages.put(activity.getString(R.string.root), "/")

		val rList = activity.config.getRemotes()
		for(r in rList) storages.put(r.value.name, r.value.basePath)

		if(storages.size == 1) callback(storages.values.first())
		else initDialog()
	}

	private fun initDialog() {
		val basePath = path.getBasePath(activity)
		val inflater = LayoutInflater.from(activity)
		val layout = RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		val view = DialogRadioGroupBinding.inflate(inflater, null, false)
		var nextId = 0
		radioGroup = view.dialogRadioGroup

		for(s in storages) {
			val btn = RadioButtonBinding.inflate(inflater, null, false).root
			btn.apply {
				id = nextId++
				text = s.key
				isChecked = basePath == s.value
				if(isChecked) defaultId = id
				setOnClickListener {
					if(s.value == activity.otgPath) handleOTG(s.value)
					else handlePick(s.value)
				}
			}
			radioGroup.addView(btn, layout)
		}

		activity.setupDialogStuff(view.root, activity.getAlertDialogBuilder(),
			R.string.select_storage) {dialog = it}
	}

	private fun handlePick(path: String) {
		dialog?.dismiss()
		callback(path)
	}

	private fun handleOTG(path: String) {
		activity.handleOTGPermission {
			if(it) handlePick(path)
			else radioGroup.check(defaultId)
		}
	}
}