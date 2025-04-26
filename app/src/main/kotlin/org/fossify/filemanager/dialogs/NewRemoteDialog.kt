package org.fossify.filemanager.dialogs

import androidx. appcompat.app.AlertDialog
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.views.MyAppCompatSpinner
import org.fossify.filemanager.databinding.DialogNewRemoteBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.helpers.Remote
import org.fossify.filemanager.helpers.TYPES

class NewRemoteDialog(val activity: BaseSimpleActivity, val rEdit: Remote?, val callback: ()->Unit) {
	private val binding = DialogNewRemoteBinding.inflate(activity.layoutInflater)
	private lateinit var diag: AlertDialog

	init {
		activity.getAlertDialogBuilder().apply {
			setPositiveButton(R.string.ok, null)
			setNegativeButton(R.string.cancel, null)
			activity.setupDialogStuff(binding.root, this, org.fossify.filemanager.R.string.new_remote,
					cancelOnTouchOutside = false) {
				diag = it
				initDialog()
			}
		}
	}

	private fun initDialog() {
		diag.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(::onOk)
		val tl = arrayListOf<String>(activity.getString(org.fossify.filemanager.R.string.type))
		tl.addAll(TYPES)
		binding.type.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, tl)
		setColors(binding.type)
		binding.type.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onItemSelected(a: AdapterView<*>?, v: View?, p: Int, i: Long) {onSetType(v)}
			override fun onNothingSelected(a: AdapterView<*>?) {}
		}

		//TODO if rEdit, set everything to the existing vals
	}

	private fun onSetType(v: View?) {
		setColors(v)
		var t = TYPES.indexOf(text(v as TextView))
		parent(binding.host).beVisibleIf(t == 0)
		parent(binding.usr).beVisibleIf(t == 0)
		parent(binding.pwd).beVisibleIf(t == 0)
		parent(binding.share).beVisibleIf(t == 0)
		parent(binding.domain).beVisibleIf(t == 0)
	}

	private fun setColors(v: View?) {
		val txtColor = activity.getProperTextColor()
		val accColor = activity.getProperPrimaryColor()
		val bgColor = activity.getProperBackgroundColor()
		if(v is MyAppCompatSpinner) v.setColors(txtColor, accColor, bgColor)
		else if(v is TextView) v.setTextColor(txtColor)
	}

	private fun onOk(v: View) {
		//TODO if rEdit, update instead of creating new remote
		val t = TYPES.indexOf(text(binding.type.selectedView as? TextView))
		if(t == 0) {
			try {
				val r = Remote.newSMB(text(binding.name), text(binding.host),
					text(binding.usr), text(binding.share), text(binding.domain))
				r.setPwd(text(binding.pwd))
				r.connect()
				activity.config.addRemote(r)
				diag.dismiss()
				callback()
			} catch(e: Throwable) {Remote.err(activity, e)}
		} else activity.toast(org.fossify.filemanager.R.string.remote_type_err)
	}

	private fun parent(v: View) = v.parent.parent as View
	private fun text(tv: TextView?) = tv?.text?.toString()?.trim()?:""
}