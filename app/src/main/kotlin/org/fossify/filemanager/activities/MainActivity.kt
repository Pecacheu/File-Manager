package org.fossify.filemanager.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.util.AttributeSet
import android.util.Xml
import android.view.KeyEvent
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContextView
import com.stericson.RootTools.RootTools
import me.grantland.widget.AutofitHelper
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
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
import org.fossify.filemanager.fragments.ItemsFragment
import org.fossify.filemanager.fragments.MyViewPagerFragment
import org.fossify.filemanager.fragments.RecentsFragment
import org.fossify.filemanager.fragments.FavoritesFragment
import org.fossify.filemanager.fragments.StorageFragment
import java.io.File
import androidx.viewpager2.widget.ViewPager2
import org.fossify.commons.helpers.*
import org.fossify.commons.views.MySearchMenu
import org.fossify.filemanager.about.AboutActivityAlt
import org.fossify.filemanager.extensions.*
import org.fossify.filemanager.helpers.*
import org.fossify.filemanager.models.ListItem
import java.util.Date
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class MainActivity: SimpleActivity() {
	override var isSearchBarEnabled = true

	companion object {
		private const val BACK_PRESS_TIMEOUT = 5000
		const val NEW_REMOTE_RC = 202
		private const val LAST_SEARCH = "last_search"
	}

	private val binding by viewBinding(ActivityMainBinding::inflate)
	private lateinit var mainMenu: MySearchMenu
	private lateinit var menuAttr: AttributeSet

	private var wasBackJustPressed = false
	private var mTabsToShow = ArrayList<Int>()
	private var mStoredFontSize = 0
	private var mStoredDateFormat = ""
	private var mStoredTimeFormat = ""
	private var scrollTmr: Timer? = null

	@SuppressLint("RestrictedApi")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if(config.lastVersion < 4) {
			if(config.showTabs and TAB_STORAGE_ANALYSIS == 0) config.showTabs += TAB_STORAGE_ANALYSIS
			if(config.showTabs and TAB_FAVORITES == 0) config.showTabs += TAB_FAVORITES
			config.setHome(config.internalStoragePath)
		}

		setContentView(binding.root)
		appLaunched(BuildConfig.APPLICATION_ID)
		mainMenu = binding.mainMenu
		setupOptionsMenu()
		storeStateVariables()
		setupTabs(getTabsToShow())

		setupViews(binding.mainCoordinator, null, null, null) {iAll, iNav ->
			val mm = mainMenu.layoutParams as MarginLayoutParams
			mm.setMargins(iAll.left, 0, iAll.right, 0)

			for(frag in getAllFragments()) frag?.recyclerAdapter?.actMode?.let {
				val tb = it.customView.parent as ActionBarContextView
				tb.setPadding(iAll.left, iAll.top, iAll.right, 0)
				tb.contentHeight = mainMenu.measuredHeight
				(tb.layoutParams as MarginLayoutParams).setMargins(iNav.left, 0, iNav.right, 0)
			}

			binding.mainViewPager.setPadding(iAll.left, 0, iAll.right, 0)

			binding.mainTabsHolder.setPadding(0, 0, 0, iAll.bottom)
			val mt = binding.mainTabsHolder.layoutParams as MarginLayoutParams
			mt.setMargins(iNav.left, 0, iNav.right, 0)
		}

		if(savedInstanceState == null) {
			config.temporarilyShowHidden = false
			initFragments()
			checkInvalidFavorites()
			tryInitFileManager()
			checkWhatsNewDialog()
			checkIfRootAvailable()
		}

		val parser = resources.getXml(R.xml.search_view)
		menuAttr = Xml.asAttributeSet(parser)
	}

	override fun onResume() {
		super.onResume()
		refreshMenuItems()
		updateMenuColors()
		updateFavsList(true)
		setupTabColors()

		for(f in getAllFragments()) f?.onResume(getProperTextColor())
		if(mStoredFontSize != config.fontSize) {
			for(f in getAllFragments()) f?.setupFontSize()
		}
		if(mStoredDateFormat != config.dateFormat || mStoredTimeFormat != getTimeFormat()) {
			for(f in getAllFragments()) f?.setupDateTimeFormat()
		}
		if(binding.mainViewPager.adapter == null) initFragments()
		else if(config.reloadPath) openPath(config.lastPath)
		config.reloadPath = false
	}

	override fun onPause() {
		super.onPause()
		storeStateVariables()
		config.lastUsedViewPagerPage = tabIdxToId(binding.mainViewPager.currentItem)
		config.lastPath = getItemsFragment()?.currentPath?:""
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		updateFragmentColumnCounts()

		//Reload Main Menu
		val search = mainMenu.binding.topToolbarSearch.text
		binding.mainCoordinator.removeView(mainMenu)
		mainMenu = MySearchMenu(this, menuAttr)
		binding.mainCoordinator.addView(mainMenu)
		setupOptionsMenu()
		refreshMenuItems()
		mainMenu.updateColors()
		if(search.isNotEmpty()) {
			mainMenu.binding.topToolbarSearch.text = search
			mainMenu.post {mainMenu.focusView()}
		}
	}

	override fun onBackPressedCompat(): Boolean {
		val fragment = getCurrentFragment()
		if(mainMenu.isSearchOpen) {
			mainMenu.closeSearch()
			return true
		} else if(fragment !is ItemsFragment) {
			return false
		} else if(fragment.getBreadcrumbs().getItemCount() <= 1) {
			if (!wasBackJustPressed && config.pressBackTwice) {
				wasBackJustPressed = true
				toast(R.string.press_back_again)
				Handler().postDelayed({wasBackJustPressed = false},
					BACK_PRESS_TIMEOUT.toLong())
				return true
			} else {
				appLockManager.lock()
				finish()
				return true
			}
		} else {
			fragment.getBreadcrumbs().removeBreadcrumb()
			openPath(fragment.getBreadcrumbs().getLastItem().path)
			return true
		}
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		if(!mainMenu.isSearchOpen) {
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
			runOnUiThread {getCurrentFragment()?.getRecyclerView()?.scrollBy(0, by)}
		}
	}
	private fun endScroll() {
		scrollTmr?.cancel()
		scrollTmr = null
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		if(!mainMenu.isSearchOpen) {
			if(event?.isCtrlPressed == true) {
				val adapter = getCurrentFragment()?.recyclerAdapter
				if(keyCode == KeyEvent.KEYCODE_A) {
					adapter?.selectAll()
					return true
				}
				if(adapter?.isActMode() == true) when(keyCode) { //Action mode
					KeyEvent.KEYCODE_D -> {
						getCurrentFragment()?.finishActMode()
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
		val fragment = getCurrentFragment()?:return
		val isCreateDocIntent = intent.action == Intent.ACTION_CREATE_DOCUMENT
		val path = fragment.currentPath
		val viewType = config.getFolderViewType(path)
		val isFav = config.favorites.contains(path)
		val home = config.getHome(path)

		mainMenu.toolbar?.menu?.apply {
			findItem(R.id.sort).isVisible = fragment is ItemsFragment
			findItem(R.id.change_view_type).isVisible = fragment !is StorageFragment

			findItem(R.id.add_favorite).isVisible = fragment is ItemsFragment && !isFav
			findItem(R.id.remove_favorite).isVisible = fragment is ItemsFragment && isFav

			findItem(R.id.toggle_filename).isVisible = viewType == VIEW_TYPE_GRID &&
				fragment !is StorageFragment && fragment !is FavoritesFragment

			findItem(R.id.go_home).isVisible = fragment is ItemsFragment && path != home
			findItem(R.id.set_as_home).isVisible = fragment is ItemsFragment && path != home

			findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden() && fragment !is StorageFragment
			findItem(R.id.stop_showing_hidden).isVisible = config.temporarilyShowHidden && fragment !is StorageFragment
			findItem(R.id.column_count).isVisible = viewType == VIEW_TYPE_GRID && fragment !is StorageFragment

			findItem(R.id.settings).isVisible = !isCreateDocIntent
			findItem(R.id.about).isVisible = !isCreateDocIntent
		}
	}

	private fun setupOptionsMenu() {
		mainMenu.apply {
			toolbar?.inflateMenu(R.menu.menu)
			toggleHideOnScroll(false)
			setupMenu()

			onSearchClosedListener = {
				for(f in getAllFragments()) f?.searchQueryChanged("")
			}
			onSearchTextChangedListener = {text ->
				getCurrentFragment()?.searchQueryChanged(text)
			}

			toolbar?.setOnMenuItemClickListener {menuItem ->
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
		openPath(config.lastPath.ifEmpty {config.getHome("")})
		val search = state.getString(LAST_SEARCH)?:""
		if(search.isNotEmpty()) mainMenu.binding.topToolbarSearch.setText(search)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
		super.onActivityResult(requestCode, resultCode, resultData)
		if(requestCode == NEW_REMOTE_RC && resultCode == 1) {
			config.getRemotes(true) //Force reload
			resultData?.getStringExtra(REAL_FILE_PATH)?.let {openPath(it)}
		}
	}

	private fun updateMenuColors() {
		mainMenu.updateColors()
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

	private fun initFileManager(refreshRecents: Boolean) {
		var path = config.lastPath.ifEmpty {config.getHome("")}
		if(intent.action == Intent.ACTION_VIEW && intent.data != null) {
			val data = intent.data!!
			if(data.scheme == "file" && data.path != null) {
				path = data.path!!
				val pTrim = path.trimStart('/') //URI may have leading / for remote path
				if(isRemotePath(pTrim)) path = pTrim
				ensureBackgroundThread {
					try {
						val isFile = ListItem.fileExists(this, path)
						runOnUiThread {
							if(isFile) launchPath(path, false, finishActivity=true)
							else openPath(path)
						}
					} catch(e: Throwable) {error(e)}
				}
			} else openPath(getRealPathFromURI(data)?:path)
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
					for(f in getAllFragments()) f?.finishActMode()
					refreshMenuItems()
				}
			})
			currentItem = tabIdToIdx(config.lastUsedViewPagerPage)
			onGlobalLayout(::refreshMenuItems)
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
				mainMenu.closeSearch()
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
				getStorageDirectories().firstOrNull {
					it.trimEnd('/') != config.internalStoragePath && it.trimEnd('/') != config.sdCardPath
				}?.apply {
					config.wasOTGHandled = true
					config.OTGPath = trimEnd('/')
				}
			}
		}
	}

	fun openPath(path: String, forceRefresh: Boolean=false) {
		getItemsFragment()?.openPath(path, forceRefresh)
	}

	fun gotoFilesTab() {
		binding.mainViewPager.currentItem = tabIdToIdx(TAB_FILES)
	}

	private fun goHome() {
		val path = getCurrentFragment()!!.currentPath
		val home = config.getHome(path)
		if(path != home) openPath(home)
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
		for(f in getAllFragments()) f?.toggleFilenameVisibility()
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
				updateFragmentColumnCounts()
				refreshMenuItems()
			}
		}
	}

	fun updateFragmentColumnCounts() {
		for(f in getAllFragments()) f?.columnCountChanged()
	}

	private fun setAsHome() {
		config.setHome(getCurrentFragment()!!.currentPath)
		refreshMenuItems()
		toast(R.string.home_folder_updated)
	}

	private fun changeViewType() {
		ChangeViewTypeDialog(this, getCurrentFragment()!!.currentPath, getCurrentFragment() is ItemsFragment) {
			for(f in getAllFragments()) f?.refreshFragment()
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
		for(f in getAllFragments()) f?.refreshFragment()
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
			putExtra(APP_PACKAGE_NAME, config.appId)
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
		config.getRemotes(true)
		ensureBackgroundThread {
			var badFavs = false
			config.favorites.forEach {
				val isBad = if(isRemotePath(it)) config.getRemoteForPath(it) == null
					else (it.startsWith(config.internalStoragePath) && !ListItem.pathExists(this,it))
				if(isBad) {
					config.removeFavorite(it)
					badFavs = true
				}
			}
			if(badFavs) updateFavsList()
		}
	}

	//TODO Remote
	//Used w/ apps that have no file access but need to work with files. Eg. Simple Calendar exporting events into a file
	fun createDocumentConfirmed(path: String) {
		val filename = intent.getStringExtra(Intent.EXTRA_TITLE)?:""
		if(filename.isEmpty()) {
			InsertFilenameDialog(this, config.getHome("")) {newFn ->
				finishCreateDocumentIntent(path, newFn)
			}
		} else finishCreateDocumentIntent(path, filename)
	}

	private fun finishCreateDocumentIntent(path: String, filename: String) {
		val resultIntent = Intent()
		val uri = getFilePublicUri(File(path, filename), BuildConfig.APPLICATION_ID)
		val type = path.getMimeType()
		resultIntent.setDataAndType(uri, type)
		resultIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
			Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
		setResult(RESULT_OK, resultIntent)
		finish()
	}

	fun openedDirectory() {
		if(mainMenu.isSearchOpen) mainMenu.closeSearch()
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