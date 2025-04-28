package org.fossify.filemanager.dialogs

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.databinding.DialogRadioGroupBinding
import org.fossify.commons.databinding.RadioButtonBinding
import org.fossify.commons.extensions.*
import org.fossify.filemanager.activities.NewRemoteActivity
import org.fossify.filemanager.extensions.getBasePath
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.isRemotePath
import org.fossify.filemanager.helpers.Remote

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
		initDialog()
	}

	private fun initDialog() {
		val basePath = path.getBasePath(activity)
		val inflater = LayoutInflater.from(activity)
		var layout = RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
					if(s.value == activity.otgPath) onOTG(s.value)
					else onPick(s.value)
				}
			}
			radioGroup.addView(btn, layout)
		}

		//Remote buttons
		val btnEdit = MaterialButton(activity)
		btnEdit.setText(R.string.edit)
		btnEdit.setOnClickListener(::onEdit)

		val btnNew = MaterialButton(activity)
		btnNew.setText(R.string.create_new)
		btnNew.setOnClickListener(::onNew)

		val btnRow = LinearLayout(activity)
		val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		p.rightMargin = activity.resources.getDimensionPixelSize(R.dimen.medium_margin)
		layout = RadioGroup.LayoutParams(layout)
		layout.topMargin = p.rightMargin
		btnRow.addView(btnEdit, p)
		btnRow.addView(btnNew)
		radioGroup.addView(btnRow, layout)

		activity.setupDialogStuff(view.root, activity.getAlertDialogBuilder(),
			R.string.select_storage) {dialog = it}
	}

	private fun onEdit(v: View) {
		if(!isRemotePath(path)) {
			activity.toast(org.fossify.filemanager.R.string.remote_edit_err)
			return
		}
		val r = activity.config.getRemoteForPath(path)
		if(r != null) launchRemote(r)
		else activity.toast(org.fossify.filemanager.R.string.no_remote_err)
	}
	private fun onNew(v: View) = launchRemote(null)

	private fun launchRemote(r: Remote?) {
		val i = Intent(activity.applicationContext, NewRemoteActivity::class.java)
		i.putExtra(NewRemoteActivity.EDIT, r?.id?.id)
		activity.startActivity(i)
		dialog?.dismiss()
	}

	private fun onPick(path: String) {
		dialog?.dismiss()
		callback(path)
	}

	private fun onOTG(path: String) {
		activity.handleOTGPermission {
			if(it) onPick(path)
			else radioGroup.check(defaultId)
		}
	}
}