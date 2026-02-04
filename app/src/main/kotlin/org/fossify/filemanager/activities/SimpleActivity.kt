package org.fossify.filemanager.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Environment
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.Window
import android.view.WindowManager
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ScrollingView
import androidx.core.view.WindowInsetsCompat
import android.provider.Settings
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_WRITE_STORAGE
import org.fossify.commons.helpers.isPiePlus
import org.fossify.commons.helpers.isRPlus
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.R
import androidx.core.net.toUri
import org.fossify.commons.views.MyAppBarLayout

open class SimpleActivity: BaseSimpleActivity() {
	var onConflict = 0

	override fun getAppIconIDs() =
		arrayListOf(R.mipmap.ic_launcher_red, R.mipmap.ic_launcher_pink, R.mipmap.ic_launcher_purple, R.mipmap.ic_launcher_deep_purple,
			R.mipmap.ic_launcher_indigo, R.mipmap.ic_launcher_blue, R.mipmap.ic_launcher_light_blue, R.mipmap.ic_launcher_cyan, R.mipmap.ic_launcher_teal,
			R.mipmap.ic_launcher, R.mipmap.ic_launcher_light_green, R.mipmap.ic_launcher_lime, R.mipmap.ic_launcher_yellow, R.mipmap.ic_launcher_amber,
			R.mipmap.ic_launcher_orange, R.mipmap.ic_launcher_deep_orange, R.mipmap.ic_launcher_brown, R.mipmap.ic_launcher_blue_grey,
			R.mipmap.ic_launcher_grey_black)

	companion object {
		private const val MANAGE_STORAGE_RC = 201
	}

	override fun getAppLauncherName() = getString(R.string.app_launcher_name)
	override fun getRepositoryName() = "File-Manager"

	@SuppressLint("NewApi")
	override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
		super.onActivityResult(requestCode, resultCode, resultData)
		isAskingPermissions = false
		if(requestCode == MANAGE_STORAGE_RC && isRPlus()) {
			actionOnPermission?.invoke(Environment.isExternalStorageManager())
		}
	}

	@SuppressLint("InlinedApi")
	fun handleStoragePermission(callback: (granted: Boolean) -> Unit) {
		actionOnPermission = null
		if(hasStoragePermission()) callback(true)
		else if(isRPlus()) {
			ConfirmationAdvancedDialog(this, "", org.fossify.commons.R.string.access_storage_prompt,
					org.fossify.commons.R.string.ok, 0, false) {
				if(it) {
					isAskingPermissions = true
					actionOnPermission = callback
					try {
						val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
						intent.addCategory("android.intent.category.DEFAULT")
						intent.data = "package:$packageName".toUri()
						startActivityForResult(intent, MANAGE_STORAGE_RC)
					} catch(_: android.content.ActivityNotFoundException) {
						val intent = Intent()
						intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
						startActivityForResult(intent, MANAGE_STORAGE_RC)
					} catch (e: SecurityException) {
						error(e)
						finish()
					}
				} else finish()
			}
		} else handlePermission(PERMISSION_WRITE_STORAGE, callback)
	}

	fun hasStoragePermission(): Boolean {
		return if(isRPlus()) Environment.isExternalStorageManager()
		else hasPermission(PERMISSION_WRITE_STORAGE)
	}

	override fun setContentView(view: View?) {
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		if(isPiePlus()) {
			window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
			window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
		}
		super.setContentView(view)
	}

	fun setupViews(main: CoordinatorLayout, content: View?, appbar: MyAppBarLayout?, scrollView: ScrollingView?,
			onInsets: ((iAll: Insets, iNav: Insets)->Unit)?=null) {
		if(scrollView != null) setupEdgeToEdge(padBottomSystem = listOf(scrollView as View))
		if(appbar != null) setupMaterialScrollListener(scrollView, appbar)

		window.decorView.setOnApplyWindowInsetsListener {v, insets ->
			val ins = WindowInsetsCompat.toWindowInsetsCompat(insets, v)
			val iAll = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val iNav = ins.getInsets(WindowInsetsCompat.Type.navigationBars())

			val mc = main.layoutParams as MarginLayoutParams
			mc.setMargins(0, 0, 0, 0)

			if(content != null) {
				val cc = content.layoutParams as MarginLayoutParams
				cc.setMargins(iAll.left, cc.topMargin, iAll.right, iAll.bottom)
			}

			onInsets?.invoke(iAll, iNav)
			insets
		}
	}
}