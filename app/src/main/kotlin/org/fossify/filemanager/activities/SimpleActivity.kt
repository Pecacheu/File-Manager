package org.fossify.filemanager.activities

import android.content.res.Configuration
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ScrollingView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.helpers.PERMISSION_WRITE_STORAGE
import org.fossify.commons.helpers.isPiePlus
import org.fossify.commons.helpers.isRPlus
import org.fossify.filemanager.R

open class SimpleActivity: BaseSimpleActivity() {
	var onConflict = 0
	private var tbHeight = 0

	override fun getAppIconIDs() =
		arrayListOf(R.mipmap.ic_launcher_red, R.mipmap.ic_launcher_pink, R.mipmap.ic_launcher_purple, R.mipmap.ic_launcher_deep_purple,
			R.mipmap.ic_launcher_indigo, R.mipmap.ic_launcher_blue, R.mipmap.ic_launcher_light_blue, R.mipmap.ic_launcher_cyan, R.mipmap.ic_launcher_teal,
			R.mipmap.ic_launcher, R.mipmap.ic_launcher_light_green, R.mipmap.ic_launcher_lime, R.mipmap.ic_launcher_yellow, R.mipmap.ic_launcher_amber,
			R.mipmap.ic_launcher_orange, R.mipmap.ic_launcher_deep_orange, R.mipmap.ic_launcher_brown, R.mipmap.ic_launcher_blue_grey,
			R.mipmap.ic_launcher_grey_black)

	override fun getAppLauncherName() = getString(R.string.app_launcher_name)
	override fun getRepositoryName() = "File-Manager"

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

	fun setupViews(main: CoordinatorLayout?, content: View?, toolbar: Toolbar?, scrollView: ScrollingView?,
			onInsets: ((iAll: Insets, iNav: Insets)->Unit)?=null) {
		updateMaterialActivityViews(main, content, false, toolbar == null)
		if(toolbar != null) setupMaterialScrollListener(scrollView, toolbar)

		fun setTbInsets(iAll: Insets) {
			toolbar!!.setPadding(iAll.left, iAll.top, iAll.right, 0)
			toolbar.layoutParams.height = tbHeight + iAll.top

			(scrollView as ViewGroup).setPadding(iAll.left, 0, iAll.right, 0)
			val sv = scrollView.layoutParams as MarginLayoutParams
			if(scrollView is NestedScrollView) sv.setMargins(0, tbHeight + iAll.top, 0, 0)
			else sv.setMargins(0, iAll.top, 0, 0)

			val svc = scrollView.children.lastOrNull()
			svc?.setPadding(svc.paddingLeft, svc.paddingTop, svc.paddingRight, svc.paddingBottom + iAll.bottom)
		}

		window.decorView.setOnApplyWindowInsetsListener {v, insets ->
			val ins = WindowInsetsCompat.toWindowInsetsCompat(insets, v)
			val iAll = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val iNav = ins.getInsets(WindowInsetsCompat.Type.navigationBars())

			if(main != null && content != null) {
				val mc = main.layoutParams as MarginLayoutParams
				mc.setMargins(0, 0, 0, 0)
			}
			if(toolbar != null && scrollView != null) {
				if(tbHeight == 0) toolbar.onGlobalLayout {
					tbHeight = toolbar.measuredHeight
					setTbInsets(iAll)
				} else setTbInsets(iAll)
			}

			onInsets?.invoke(iAll, iNav)
			insets
		}
	}
}