package org.fossify.filemanager.activities

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.view.KeyEvent
import android.widget.TextView
import com.stericson.RootTools.RootTools
import me.grantland.widget.AutofitHelper
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.getBottomNavigationBackgroundColor
import org.fossify.commons.extensions.getFilePublicUri
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.getStorageDirectories
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.hasOTGConnected
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.onTabSelectionChanged
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateBottomTabItemColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_AUTOFITTEXTVIEW
import org.fossify.commons.helpers.LICENSE_GESTURE_VIEWS
import org.fossify.commons.helpers.LICENSE_GLIDE
import org.fossify.commons.helpers.LICENSE_PATTERN
import org.fossify.commons.helpers.LICENSE_REPRINT
import org.fossify.commons.helpers.LICENSE_ZIP4J
import org.fossify.commons.helpers.PERMISSION_WRITE_STORAGE
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_FILES
import org.fossify.commons.helpers.TAB_RECENT_FILES
import org.fossify.commons.helpers.TAB_STORAGE_ANALYSIS
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.filemanager.BuildConfig
import org.fossify.filemanager.R
import org.fossify.filemanager.adapters.ViewPagerAdapter
import org.fossify.filemanager.databinding.ActivityMainBinding
import org.fossify.filemanager.dialogs.ChangeSortingDialog
import org.fossify.filemanager.dialogs.ChangeViewTypeDialog
import org.fossify.filemanager.dialogs.InsertFilenameDialog
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.tryOpenPathIntent
import org.fossify.filemanager.fragments.ItemsFragment
import org.fossify.filemanager.fragments.MyViewPagerFragment
import org.fossify.filemanager.fragments.RecentsFragment
import org.fossify.filemanager.fragments.FavoritesFragment
import org.fossify.filemanager.fragments.StorageFragment
import org.fossify.filemanager.helpers.MAX_COLUMN_COUNT
import org.fossify.filemanager.helpers.RootHelpers
import org.fossify.filemanager.interfaces.ItemOperationsListener
import java.io.File
import androidx.core.net.toUri
import androidx.viewpager2.widget.ViewPager2
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.APP_FAQ
import org.fossify.commons.helpers.APP_ICON_IDS
import org.fossify.commons.helpers.APP_LAUNCHER_NAME
import org.fossify.commons.helpers.APP_LICENSES
import org.fossify.commons.helpers.APP_NAME
import org.fossify.commons.helpers.APP_PACKAGE_NAME
import org.fossify.commons.helpers.APP_REPOSITORY_NAME
import org.fossify.commons.helpers.APP_VERSION_NAME
import org.fossify.filemanager.about.AboutActivityAlt
import java.util.Date
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class MainActivity: SimpleActivity() {
	companion object {
		private const val BACK_PRESS_TIMEOUT = 5000
		private const val MANAGE_STORAGE_RC = 201
		private const val LAST_SEARCH = "last_search"
	}

	private val binding by viewBinding(ActivityMainBinding::inflate)

	private var wasBackJustPressed = false
	private var mTabsToShow = ArrayList<Int>()
	private var mStoredFontSize = 0
	private var mStoredDateFormat = ""
	private var mStoredTimeFormat = ""
	private var scrollTmr: Timer? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		isMaterialActivity = true
		super.onCreate(savedInstanceState)
		setContentView(binding.root)
		appLaunched(BuildConfig.APPLICATION_ID)
		setupOptionsMenu()

		if(config.lastVersion < 3) {
			if(config.showTabs and TAB_STORAGE_ANALYSIS == 0) config.showTabs += TAB_STORAGE_ANALYSIS
			if(config.showTabs and TAB_FAVORITES == 0) config.showTabs += TAB_FAVORITES
		}

		storeStateVariables()
		setupTabs(getTabsToShow())
		refreshMenuItems()

		updateMaterialActivityViews(binding.mainCoordinator, null, useTransparentNavigation = false, useTopSearchMenu = true)

		if(savedInstanceState == null) {
			initFragments()
			tryInitFileManager()
			checkWhatsNewDialog()
			checkIfRootAvailable()
			checkInvalidFavorites()
		}
	}

	override fun onResume() {
		super.onResume()
		refreshMenuItems()
		updateMenuColors()
		updateFavsList(true)
		setupTabColors()

		getAllFragments().forEach {it?.onResume(getProperTextColor())}
		if(mStoredFontSize != config.fontSize) {
			getAllFragments().forEach {(it as? ItemOperationsListener)?.setupFontSize()}
		}
		if(mStoredDateFormat != config.dateFormat || mStoredTimeFormat != getTimeFormat()) {
			getAllFragments().forEach {(it as? ItemOperationsListener)?.setupDateTimeFormat()}
		}
		if(binding.mainViewPager.adapter == null) initFragments()
	}

	override fun onPause() {
		super.onPause()
		storeStateVariables()
		config.lastUsedViewPagerPage = tabIdxToId(binding.mainViewPager.currentItem)
		config.lastPath = getItemsFragment()?.currentPath?:""
	}

	override fun onDestroy() {
		super.onDestroy()
		config.temporarilyShowHidden = false
	}

	override fun onBackPressed() {
		val currentFragment = getCurrentFragment()
		if(binding.mainMenu.isSearchOpen) {
			binding.mainMenu.closeSearch()
		} else if(currentFragment !is ItemsFragment) {
			super.onBackPressed()
		} else if(currentFragment.getBreadcrumbs().getItemCount() <= 1) {
			if(!wasBackJustPressed && config.pressBackTwice) {
				wasBackJustPressed = true
				toast(R.string.press_back_again)
				Handler().postDelayed({
					wasBackJustPressed = false
				}, BACK_PRESS_TIMEOUT.toLong())
			} else {
				appLockManager.lock()
				finish()
			}
		} else {
			currentFragment.getBreadcrumbs().removeBreadcrumb()
			openPath(currentFragment.getBreadcrumbs().getLastItem().path)
		}
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		if(!binding.mainMenu.isSearchOpen) {
			when(keyCode) {
				KeyEvent.KEYCODE_DPAD_UP -> {
					beginScroll(-50)
					return true
				} KeyEvent.KEYCODE_DPAD_DOWN -> {
					beginScroll(50)
					return true
				}
			}
		}
		return super.onKeyDown(keyCode, event)
	}

	private fun beginScroll(by: Int) {
		endScroll()
		scrollTmr = fixedRateTimer(startAt = Date(), period = 30) {
			runOnUiThread {getCurrentFragment()?.getRecyclerAdapter()?.recyclerView?.scrollBy(0, by)}
		}
	}
	private fun endScroll() {
		scrollTmr?.cancel()
		scrollTmr = null
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		if(!binding.mainMenu.isSearchOpen) {
			if(event?.isCtrlPressed == true) {
				val adapter = getCurrentFragment()?.getRecyclerAdapter()
				if(keyCode == KeyEvent.KEYCODE_A) {
					adapter?.doSelectAll()
					return true
				}
				if(adapter?.isActMode() == true) when(keyCode) { //Action mode
					KeyEvent.KEYCODE_D -> {
						(getCurrentFragment() as? ItemOperationsListener)?.finishActMode()
						return true
					} KeyEvent.KEYCODE_S -> {
						adapter.shareFiles()
						return true
					} KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_X -> {
						adapter.copyMoveTo(keyCode == KeyEvent.KEYCODE_C)
						return true
					} KeyEvent.KEYCODE_R -> {
						adapter.displayRenameDialog()
						return true
					} KeyEvent.KEYCODE_I -> {
						adapter.showProperties()
						return true
					}
				} else when(keyCode) { //Main view
					KeyEvent.KEYCODE_DPAD_LEFT -> {
						binding.mainViewPager.currentItem -= 1
						return true
					} KeyEvent.KEYCODE_DPAD_RIGHT -> {
						binding.mainViewPager.currentItem += 1
						return true
					}
				}
			}
		}
		when(keyCode) {
			KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
				endScroll()
				return true
			}
		}
		return super.onKeyUp(keyCode, event)
	}

	fun refreshMenuItems() {
		val currentFragment = getCurrentFragment()?:return
		val isCreateDocumentIntent = intent.action == Intent.ACTION_CREATE_DOCUMENT
		val currentViewType = config.getFolderViewType(currentFragment.currentPath)
		val favorites = config.favorites

		binding.mainMenu.getToolbar().menu.apply {
			findItem(R.id.sort).isVisible = currentFragment is ItemsFragment
			findItem(R.id.change_view_type).isVisible = currentFragment !is StorageFragment

			findItem(R.id.add_favorite).isVisible = currentFragment is ItemsFragment && !favorites.contains(currentFragment.currentPath)
			findItem(R.id.remove_favorite).isVisible = currentFragment is ItemsFragment && favorites.contains(currentFragment.currentPath)

			findItem(R.id.toggle_filename).isVisible = currentViewType == VIEW_TYPE_GRID &&
				currentFragment !is StorageFragment && currentFragment !is FavoritesFragment

			findItem(R.id.go_home).isVisible = currentFragment is ItemsFragment && currentFragment.currentPath != config.homeFolder
			findItem(R.id.set_as_home).isVisible = currentFragment is ItemsFragment && currentFragment.currentPath != config.homeFolder

			findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden() && currentFragment !is StorageFragment
			findItem(R.id.stop_showing_hidden).isVisible = config.temporarilyShowHidden && currentFragment !is StorageFragment
			findItem(R.id.column_count).isVisible = currentViewType == VIEW_TYPE_GRID && currentFragment !is StorageFragment

			findItem(R.id.settings).isVisible = !isCreateDocumentIntent
			findItem(R.id.about).isVisible = !isCreateDocumentIntent
		}
	}

	private fun setupOptionsMenu() {
		binding.mainMenu.apply {
			getToolbar().inflateMenu(R.menu.menu)
			toggleHideOnScroll(false)
			setupMenu()

			onSearchClosedListener = {
				getAllFragments().forEach {it?.searchQueryChanged("")}
			}
			onSearchTextChangedListener = {text ->
				getCurrentFragment()?.searchQueryChanged(text)
			}

			getToolbar().setOnMenuItemClickListener {menuItem ->
				if(getCurrentFragment() == null) return@setOnMenuItemClickListener true
				when(menuItem.itemId) {
					R.id.go_home -> goHome()
					R.id.sort -> showSortingDialog()
					R.id.add_favorite -> addFavorite()
					R.id.remove_favorite -> removeFavorite()
					R.id.toggle_filename -> toggleFilenameVisibility()
					R.id.set_as_home -> setAsHome()
					R.id.change_view_type -> changeViewType()
					R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
					R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
					R.id.column_count -> changeColumnCount()
					R.id.settings -> launchSettings()
					R.id.about -> launchAbout()
					else -> return@setOnMenuItemClickListener false
				}
				return@setOnMenuItemClickListener true
			}
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(LAST_SEARCH, getCurrentFragment()?.lastSearchedText?:"")
	}

	override fun onRestoreInstanceState(state: Bundle) {
		super.onRestoreInstanceState(state)
		if(binding.mainViewPager.adapter == null) binding.mainViewPager.onGlobalLayout {restoreState(state)}
		else restoreState(state)
	}

	private fun restoreState(state: Bundle) {
		openPath(config.lastPath.ifEmpty {config.homeFolder})

		val search = state.getString(LAST_SEARCH)?:""
		if(search.isNotEmpty()) binding.mainMenu.binding.topToolbarSearch.setText(search)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
		super.onActivityResult(requestCode, resultCode, resultData)
		isAskingPermissions = false
		if(requestCode == MANAGE_STORAGE_RC && isRPlus()) {
			actionOnPermission?.invoke(Environment.isExternalStorageManager())
		}
	}

	private fun updateMenuColors() {
		updateStatusbarColor(getProperBackgroundColor())
		binding.mainMenu.updateColors()
	}

	private fun storeStateVariables() {
		config.apply {
			mStoredFontSize = fontSize
			mStoredDateFormat = dateFormat
			mStoredTimeFormat = context.getTimeFormat()
		}
	}

	private fun tryInitFileManager() {
		val hadPermission = hasStoragePermission()
		handleStoragePermission {
			checkOTGPath()
			if(it) {
				if(binding.mainViewPager.adapter == null) initFragments()
				binding.mainViewPager.onGlobalLayout {initFileManager(!hadPermission)}
			} else {
				toast(org.fossify.commons.R.string.no_storage_permissions)
				finish()
			}
		}
	}

	private fun handleStoragePermission(callback: (granted: Boolean)->Unit) {
		actionOnPermission = null
		if(hasStoragePermission()) {
			callback(true)
		} else {
			if(isRPlus()) {
				ConfirmationAdvancedDialog(this, "", org.fossify.commons.R.string.access_storage_prompt, org.fossify.commons.R.string.ok, 0, false) {success ->
					if(success) {
						isAskingPermissions = true
						actionOnPermission = callback
						try {
							val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
							intent.addCategory("android.intent.category.DEFAULT")
							intent.data = "package:$packageName".toUri()
							startActivityForResult(intent, MANAGE_STORAGE_RC)
						} catch(e: Exception) {
							showErrorToast(e)
							val intent = Intent()
							intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
							startActivityForResult(intent, MANAGE_STORAGE_RC)
						}
					} else {
						finish()
					}
				}
			} else {
				handlePermission(PERMISSION_WRITE_STORAGE, callback)
			}
		}
	}

	private fun initFileManager(refreshRecents: Boolean) {
		val path = config.lastPath.ifEmpty {config.homeFolder}
		if(intent.action == Intent.ACTION_VIEW && intent.data != null) {
			val data = intent.data
			if(data?.scheme == "file") openPath(data.path!!)
			else openPath(getRealPathFromURI(data!!)?:path)

			if(!File(data.path!!).isDirectory)
				tryOpenPathIntent(data.path!!, false, finishActivity = true)

			binding.mainViewPager.currentItem = 0
		} else openPath(path)
		if(refreshRecents) getRecentsFragment()?.refreshFragment()
	}

	private fun initFragments() {
		binding.mainViewPager.apply {
			adapter = ViewPagerAdapter(this@MainActivity, mTabsToShow)
			registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
				override fun onPageSelected(position: Int) {
					binding.mainTabsHolder.getTabAt(position)?.select()
					getAllFragments().forEach {(it as? ItemOperationsListener)?.finishActMode()}
					refreshMenuItems()
				}
			})
			currentItem = tabIdToIdx(config.lastUsedViewPagerPage)
			onGlobalLayout {refreshMenuItems()}
		}
	}

	fun setSwipeEnabled(en: Boolean) {
		binding.mainViewPager.isUserInputEnabled = en
	}

	private fun getTabsToShow(): ArrayList<Int> {
		val action = intent.action
		if(action == Intent.ACTION_CREATE_DOCUMENT) return arrayListOf(TAB_FILES)

		val tabs = arrayListOf(TAB_FILES, TAB_FAVORITES, TAB_RECENT_FILES, TAB_STORAGE_ANALYSIS)
		if(config.favorites.isEmpty()) tabs.remove(TAB_FAVORITES)
		if(action == RingtoneManager.ACTION_RINGTONE_PICKER || action == Intent.ACTION_GET_CONTENT
			|| action == Intent.ACTION_PICK) tabs.remove(TAB_STORAGE_ANALYSIS) //Pick Document
		tabs.removeAll {it != TAB_FILES && config.showTabs and it == 0}
		return tabs
	}

	private fun setupTabs(tabs: ArrayList<Int>) {
		binding.mainTabsHolder.removeAllTabs()
		mTabsToShow = tabs

		mTabsToShow.forEach {id ->
			binding.mainTabsHolder.newTab().setCustomView(org.fossify.commons.R.layout.bottom_tablayout_item).apply {
				customView?.findViewById<TextView>(org.fossify.commons.R.id.tab_item_label)?.text = getTabLabel(id)
				AutofitHelper.create(customView?.findViewById(org.fossify.commons.R.id.tab_item_label))
				binding.mainTabsHolder.addTab(this)
			}
		}

		binding.mainTabsHolder.apply {
			onTabSelectionChanged(tabUnselectedAction = {
				updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawable(it.position))
			}, tabSelectedAction = {
				binding.mainMenu.closeSearch()
				binding.mainViewPager.currentItem = it.position
				updateBottomTabItemColors(it.customView, true, getSelectedTabDrawable(it.position))
			})
			beGoneIf(tabCount == 1)
		}
	}

	private fun setupTabColors() {
		binding.apply {
			val activeView = mainTabsHolder.getTabAt(mainViewPager.currentItem)?.customView
			updateBottomTabItemColors(activeView, true, getSelectedTabDrawable(mainViewPager.currentItem))

			getInactiveTabIndexes(mainViewPager.currentItem).forEach {index ->
				val inactiveView = mainTabsHolder.getTabAt(index)?.customView
				updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawable(index))
			}

			val bottomBarColor = getBottomNavigationBackgroundColor()
			updateNavigationBarColor(bottomBarColor)
			mainTabsHolder.setBackgroundColor(bottomBarColor)
		}
	}

	private fun getTabLabel(id: Int): String {
		val stringId = when(id) {
			TAB_FILES -> org.fossify.commons.R.string.files_tab
			TAB_FAVORITES -> org.fossify.commons.R.string.favorites
			TAB_RECENT_FILES -> R.string.recents
			else -> org.fossify.commons.R.string.storage
		}
		return resources.getString(stringId)
	}

	private fun checkOTGPath() {
		ensureBackgroundThread {
			if(!config.wasOTGHandled && hasPermission(PERMISSION_WRITE_STORAGE) && hasOTGConnected() && config.OTGPath.isEmpty()) {
				getStorageDirectories().firstOrNull {it.trimEnd('/') != internalStoragePath && it.trimEnd('/') != sdCardPath}?.apply {
					config.wasOTGHandled = true
					config.OTGPath = trimEnd('/')
				}
			}
		}
	}

	fun openPath(path: String, forceRefresh: Boolean = false) {
		var newPath = path
		val file = File(path)
		if(config.OTGPath.isNotEmpty() && config.OTGPath == path.trimEnd('/')) {
			newPath = path
		} else if(file.exists() && !file.isDirectory) {
			newPath = file.parent?.toString()?:return
		} else if(!file.exists() && !isPathOnOTG(newPath)) {
			newPath = internalStoragePath
		}
		getItemsFragment()?.openPath(newPath, forceRefresh)
	}

	fun gotoFilesTab() {
		binding.mainViewPager.currentItem = tabIdToIdx(TAB_FILES)
	}

	private fun goHome() {
		if(config.homeFolder != getCurrentFragment()!!.currentPath) {
			openPath(config.homeFolder)
		}
	}

	private fun showSortingDialog() {
		ChangeSortingDialog(this, getCurrentFragment()!!.currentPath) {
			(getCurrentFragment() as? ItemsFragment)?.refreshFragment()
		}
	}

	private fun addFavorite() {
		config.addFavorite(getCurrentFragment()!!.currentPath)
		refreshMenuItems()
		updateFavsList()
	}

	private fun removeFavorite() {
		config.removeFavorite(getCurrentFragment()!!.currentPath)
		refreshMenuItems()
		updateFavsList()
	}

	fun updateFavsList(resume: Boolean=false) {
		getFavoritesFragment()?.refreshFragment()
		val tabs = getTabsToShow()
		if(mTabsToShow != tabs) {
			setupTabs(tabs)
			if(!resume) setupTabColors()
			(binding.mainViewPager.adapter as ViewPagerAdapter).setTabs(tabs)
		}
	}

	private fun toggleFilenameVisibility() {
		config.displayFilenames = !config.displayFilenames
		getAllFragments().forEach {
			(it as? ItemOperationsListener)?.toggleFilenameVisibility()
		}
	}

	private fun changeColumnCount() {
		val items = ArrayList<RadioItem>()
		for(i in 1..MAX_COLUMN_COUNT) {
			items.add(RadioItem(i, resources.getQuantityString(org.fossify.commons.R.plurals.column_counts, i, i)))
		}
		val colCount = config.fileColumnCnt
		RadioGroupDialog(this, items, config.fileColumnCnt) {newVal ->
			val newColCount = newVal as Int
			if(colCount != newColCount) {
				config.fileColumnCnt = newColCount
				getAllFragments().forEach {(it as? ItemOperationsListener)?.columnCountChanged()}
			}
		}
	}

	fun updateFragmentColumnCounts() {
		getAllFragments().forEach {
			(it as? ItemOperationsListener)?.columnCountChanged()
		}
	}

	private fun setAsHome() {
		config.homeFolder = getCurrentFragment()!!.currentPath
		toast(R.string.home_folder_updated)
	}

	private fun changeViewType() {
		ChangeViewTypeDialog(this, getCurrentFragment()!!.currentPath, getCurrentFragment() is ItemsFragment) {
			getAllFragments().forEach {
				it?.refreshFragment()
			}
		}
	}

	private fun tryToggleTemporarilyShowHidden() {
		if(config.temporarilyShowHidden) {
			toggleTemporarilyShowHidden(false)
		} else {
			handleHiddenFolderPasswordProtection {
				toggleTemporarilyShowHidden(true)
			}
		}
	}

	private fun toggleTemporarilyShowHidden(show: Boolean) {
		config.temporarilyShowHidden = show
		getAllFragments().forEach {
			it?.refreshFragment()
		}
	}

	private fun launchSettings() {
		hideKeyboard()
		startActivity(Intent(applicationContext, SettingsActivity::class.java))
	}

	private fun launchAbout() {
		val licenses = LICENSE_GLIDE or LICENSE_PATTERN or LICENSE_REPRINT or LICENSE_GESTURE_VIEWS or LICENSE_AUTOFITTEXTVIEW or LICENSE_ZIP4J
		val faqItems = arrayListOf(FAQItem(org.fossify.commons.R.string.faq_3_title_commons, org.fossify.commons.R.string.faq_3_text_commons),
			FAQItem(org.fossify.commons.R.string.faq_9_title_commons, org.fossify.commons.R.string.faq_9_text_commons))

		if(!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
			faqItems.add(FAQItem(org.fossify.commons.R.string.faq_2_title_commons, org.fossify.commons.R.string.faq_2_text_commons))
			faqItems.add(FAQItem(org.fossify.commons.R.string.faq_6_title_commons, org.fossify.commons.R.string.faq_6_text_commons))
			faqItems.add(FAQItem(org.fossify.commons.R.string.faq_7_title_commons, org.fossify.commons.R.string.faq_7_text_commons))
			faqItems.add(FAQItem(org.fossify.commons.R.string.faq_10_title_commons, org.fossify.commons.R.string.faq_10_text_commons))
		}

		hideKeyboard()
		Intent(applicationContext, AboutActivityAlt::class.java).apply {
			putExtra(APP_ICON_IDS, getAppIconIDs())
			putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
			putExtra(APP_NAME, getString(R.string.app_name))
			putExtra(APP_REPOSITORY_NAME, getRepositoryName())
			putExtra(APP_LICENSES, licenses)
			putExtra(APP_VERSION_NAME, BuildConfig.VERSION_NAME)
			putExtra(APP_PACKAGE_NAME, baseConfig.appId)
			putExtra(APP_FAQ, faqItems)
			startActivity(this)
		}
	}

	private fun checkIfRootAvailable() {
		ensureBackgroundThread {
			config.isRootAvailable = RootTools.isRootAvailable()
			if(config.isRootAvailable && config.enableRootAccess) {
				RootHelpers(this).askRootIfNeeded {
					config.enableRootAccess = it
				}
			}
		}
	}

	private fun checkInvalidFavorites() {
		ensureBackgroundThread {
			var badFavs = false
			config.favorites.forEach {
				if(!isPathOnOTG(it) && !isPathOnSD(it) && !File(it).exists()) {
					config.removeFavorite(it)
					badFavs = true
				}
			}
			if(badFavs) updateFavsList()
		}
	}

	fun pickedPath(path: String) {
		val resultIntent = Intent()
		val uri = getFilePublicUri(File(path), BuildConfig.APPLICATION_ID)
		val type = path.getMimeType()
		resultIntent.setDataAndType(uri, type)
		resultIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
		setResult(Activity.RESULT_OK, resultIntent)
		finish()
	}

	// used at apps that have no file access at all, but need to work with files. For example Simple Calendar uses this at exporting events into a file
	fun createDocumentConfirmed(path: String) {
		val filename = intent.getStringExtra(Intent.EXTRA_TITLE)?:""
		if(filename.isEmpty()) {
			InsertFilenameDialog(this, internalStoragePath) {newFilename ->
				finishCreateDocumentIntent(path, newFilename)
			}
		} else {
			finishCreateDocumentIntent(path, filename)
		}
	}

	private fun finishCreateDocumentIntent(path: String, filename: String) {
		val resultIntent = Intent()
		val uri = getFilePublicUri(File(path, filename), BuildConfig.APPLICATION_ID)
		val type = path.getMimeType()
		resultIntent.setDataAndType(uri, type)
		resultIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
		setResult(Activity.RESULT_OK, resultIntent)
		finish()
	}

	fun pickedRingtone(path: String) {
		val uri = getFilePublicUri(File(path), BuildConfig.APPLICATION_ID)
		val type = path.getMimeType()
		Intent().apply {
			setDataAndType(uri, type)
			flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
			putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri)
			setResult(Activity.RESULT_OK, this)
		}
		finish()
	}

	fun pickedPaths(paths: ArrayList<String>) {
		val newPaths = paths.map {getFilePublicUri(File(it), BuildConfig.APPLICATION_ID)} as ArrayList
		val clipData = ClipData("Attachment", arrayOf(paths.getMimeType()), ClipData.Item(newPaths.removeAt(0)))

		newPaths.forEach {
			clipData.addItem(ClipData.Item(it))
		}

		Intent().apply {
			this.clipData = clipData
			flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
			setResult(Activity.RESULT_OK, this)
		}
		finish()
	}

	fun openedDirectory() {
		if(binding.mainMenu.isSearchOpen) binding.mainMenu.closeSearch()
	}

	private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter {it != activeIndex}

	private fun tabIdxToId(idx: Int) = mTabsToShow.getOrNull(idx)?:0
	private fun tabIdToIdx(id: Int): Int {
		val idx = mTabsToShow.indexOf(id)
		return if(idx == -1) 0 else idx
	}

	private fun getSelectedTabDrawable(idx: Int): Int {
		return when(tabIdxToId(idx)) {
			TAB_FILES -> org.fossify.commons.R.drawable.ic_folder_vector
			TAB_FAVORITES -> org.fossify.commons.R.drawable.ic_star_vector
			TAB_RECENT_FILES -> org.fossify.commons.R.drawable.ic_clock_filled_vector
			else -> R.drawable.ic_storage_vector
		}
	}

	private fun getDeselectedTabDrawable(idx: Int): Int {
		return when(tabIdxToId(idx)) {
			TAB_FILES -> org.fossify.commons.R.drawable.ic_folder_outline_vector
			TAB_FAVORITES -> org.fossify.commons.R.drawable.ic_star_outline_vector
			TAB_RECENT_FILES -> org.fossify.commons.R.drawable.ic_clock_vector
			else -> R.drawable.ic_storage_vector
		}
	}

	private fun getRecentsFragment() = findViewById<RecentsFragment>(R.id.recents_fragment)
	private fun getFavoritesFragment() = findViewById<FavoritesFragment>(R.id.favorites_fragment)
	private fun getItemsFragment() = findViewById<ItemsFragment>(R.id.items_fragment)
	private fun getStorageFragment() = findViewById<StorageFragment>(R.id.storage_fragment)
	private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> = arrayListOf(
		getItemsFragment(), getFavoritesFragment(), getRecentsFragment(), getStorageFragment())

	private fun getCurrentFragment(): MyViewPagerFragment<*>? {
		return when(tabIdxToId(binding.mainViewPager.currentItem)) {
			TAB_FILES -> getItemsFragment()
			TAB_FAVORITES -> getFavoritesFragment()
			TAB_RECENT_FILES -> getRecentsFragment()
			else -> getStorageFragment()
		}
	}

	private fun checkWhatsNewDialog() {
		arrayListOf<Release>().apply {
			checkWhatsNew(this, BuildConfig.VERSION_CODE)
		}
	}
}