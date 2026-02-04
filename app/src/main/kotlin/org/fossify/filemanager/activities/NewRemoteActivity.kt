package org.fossify.filemanager.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import org.fossify.commons.R
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyAppCompatSpinner
import org.fossify.filemanager.databinding.ActivityNewRemoteBinding
import org.fossify.filemanager.extensions.UUID
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.helpers.Remote
import kotlin.getValue

class NewRemoteActivity(): SimpleActivity() {
	companion object {
		const val EDIT = "edit"
	}
	private lateinit var unchanged: String
	private val binding by viewBinding(ActivityNewRemoteBinding::inflate)
	private var rEdit: Remote? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(binding.root)
		binding.apply {setupViews(root, holder, appbar, scroller)}
		try {
			doInit()
		} catch(e: Throwable) {
			error(e)
			finish()
		}
	}

	override fun onResume() {
		super.onResume()
		setupTopAppBar(binding.appbar, NavigationIcon.Arrow)
	}

	private fun doInit() {
		unchanged = getString(org.fossify.filemanager.R.string.pwd_unchanged)
		val id = intent.getByteArrayExtra(EDIT)?.let {UUID(it)}
		rEdit = config.getRemotes()[id.toString()]

		binding.delBtn.beVisibleIf(rEdit != null)
		binding.delBtn.setOnClickListener(::onDel)
		binding.okBtn.setOnClickListener(::onOk)

		binding.type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Remote.TYPES)
		setColors(binding.type)
		binding.type.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onItemSelected(a: AdapterView<*>?, v: View?, p: Int, i: Long) {onSetType(v)}
			override fun onNothingSelected(a: AdapterView<*>?) {}
		}

		if(rEdit != null) {
			binding.name.setText(rEdit!!.name)
			binding.toolbar.title = getString(org.fossify.filemanager.R.string.editing).format(rEdit!!.name)

			var t = rEdit!!.type
			if(t < 0 || t >= Remote.TYPES.size) t = 0
			binding.type.setSelection(t)
			if(t == Remote.SMB) {
				binding.host.setText(rEdit!!.host)
				binding.usr.setText(rEdit!!.usr)
				if(rEdit!!.pwdKey.isNotEmpty()) binding.pwd.setText(unchanged)
				binding.share.setText(rEdit!!.share)
				binding.domain.setText(rEdit!!.domain)
			}
		}
	}

	private fun onSetType(v: View?) {
		setColors(v)
		val t = getType(v)
		parent(binding.host).beVisibleIf(t == Remote.SMB)
		parent(binding.usr).beVisibleIf(t == Remote.SMB)
		parent(binding.pwd).beVisibleIf(t == Remote.SMB)
		parent(binding.share).beVisibleIf(t == Remote.SMB)
		parent(binding.domain).beVisibleIf(t == Remote.SMB)
	}

	private fun setColors(v: View?) {
		val txtColor = getProperTextColor()
		val accColor = getProperPrimaryColor()
		val bgColor = getProperBackgroundColor()
		if(v is MyAppCompatSpinner) v.setColors(txtColor, accColor, bgColor)
		else if(v is TextView) v.setTextColor(txtColor)
	}

	private fun onOk(v: View) {
		val t = getType(binding.type.selectedView)
		ensureBackgroundThread {
			if(t == Remote.SMB) {
				try {
					val d = Remote.SMBData(this, rEdit?.id, text(binding.name), text(binding.host),
						text(binding.usr), text(binding.share), text(binding.domain))

					//Edit/update remote
					val r = rEdit?.init(d)?:Remote(this.applicationContext, d)
					val pwd = text(binding.pwd)
					if(pwd != unchanged) r.setPwd(text(binding.pwd))

					//Test connection
					r.close(); r.connect()
					if(rEdit != null) config.setRemotes()
					else config.addRemote(r)
					toast(org.fossify.filemanager.R.string.test_success)
					val i = Intent()
					i.putExtra(REAL_FILE_PATH, r.basePath)
					setResult(1, i)
					finish()
				} catch(e: Throwable) {error(e)}
			} else toast(org.fossify.filemanager.R.string.remote_type_err)
		}
	}

	private fun onDel(v: View) {
		if(rEdit == null) return
		val q = String.format(getString(R.string.deletion_confirmation), rEdit!!.name)
		ConfirmationDialog(this, q) {
			config.removeRemote(rEdit!!.id)
			finish()
		}
	}

	private fun parent(v: View) = v.parent.parent as View
	private fun text(tv: TextView?) = tv?.text?.toString()?.trim()?:""
	private fun getType(sel: View?) = Remote.TYPES.indexOf(text(sel as? TextView))
}