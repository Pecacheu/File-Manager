package org.fossify.filemanager.activities

import android.app.SearchManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.R
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.ActivityMimetypesBinding
import org.fossify.filemanager.dialogs.ChangeSortingDialog
import org.fossify.filemanager.dialogs.ChangeViewTypeDialog
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.helpers.*
import org.fossify.filemanager.interfaces.ItemOperationsListener
import org.fossify.filemanager.models.ListItem
import java.util.Locale

class MimeTypesActivity: SimpleActivity(), ItemOperationsListener {
	private val binding by viewBinding(ActivityMimetypesBinding::inflate)
	private val layoutManager = binding.mimetypesList.layoutManager as MyGridLayoutManager
	private var isSearchOpen = false
	private var currentMimeType = ""
	private var lastSearchedText = ""
	private var searchMenuItem: MenuItem? = null
	private var zoomListener: MyRecyclerView.MyZoomListener? = null
	private var storedItems = ArrayList<ListItem>()
	private var currentViewType = VIEW_TYPE_LIST
	private var currentVolume = PRIMARY_VOLUME_NAME
	private var fileColumnCnt = 1

	override fun onCreate(savedInstanceState: Bundle?) {
		isMaterialActivity = true
		super.onCreate(savedInstanceState)
		setContentView(binding.root)
		setupOptionsMenu()
		binding.apply {
			updateMaterialActivityViews(mimetypesCoordinator, mimetypesList, useTransparentNavigation = true, useTopSearchMenu = false)
			setupMaterialScrollListener(mimetypesList, mimetypesToolbar)
		}
		currentMimeType = intent.getStringExtra(SHOW_MIMETYPE)?:return
		currentVolume = intent.getStringExtra(VOLUME_NAME)?:currentVolume
		binding.mimetypesToolbar.title = getString(when(currentMimeType) {
			IMAGES -> R.string.images
			VIDEOS -> R.string.videos
			AUDIO -> R.string.audio
			DOCUMENTS -> R.string.documents
			ARCHIVES -> R.string.archives
			OTHERS -> R.string.others
			else -> {
				toast(org.fossify.commons.R.string.unknown_error_occurred)
				finish()
				return
			}
		})
		ensureBackgroundThread {reFetchItems()}
		binding.apply {
			mimetypesFastscroller.updateColors(getProperPrimaryColor())
			mimetypesPlaceholder.setTextColor(getProperTextColor())
			mimetypesPlaceholder2.setTextColor(getProperTextColor())
		}
	}

	override fun onResume() {
		super.onResume()
		setupToolbar(binding.mimetypesToolbar, NavigationIcon.Arrow, searchMenuItem = searchMenuItem)
	}

	private fun setupOptionsMenu() {
		setupSearch(binding.mimetypesToolbar.menu)
		binding.mimetypesToolbar.setOnMenuItemClickListener {menuItem ->
			when(menuItem.itemId) {
				R.id.sort -> showSortingDialog()
				R.id.change_view_type -> changeViewType()
				else -> return@setOnMenuItemClickListener false
			}
			return@setOnMenuItemClickListener true
		}
	}

	override fun refreshFragment() {reFetchItems()}
	override fun selectedPaths(paths: ArrayList<String>) {}

	fun searchQueryChanged(text: String) {
		val searchText = text.trim()
		lastSearchedText = searchText
		when {
			searchText.isEmpty() -> {
				binding.apply {
					mimetypesFastscroller.beVisible()
					getRecyclerAdapter()?.updateItems(storedItems)
					mimetypesPlaceholder.beGoneIf(storedItems.isNotEmpty())
					mimetypesPlaceholder2.beGone()
				}
			}
			searchText.length == 1 -> {
				binding.apply {
					mimetypesFastscroller.beGone()
					mimetypesPlaceholder.beVisible()
					mimetypesPlaceholder2.beVisible()
				}
			} else -> {
				ensureBackgroundThread {
					if(lastSearchedText != searchText) return@ensureBackgroundThread
					val listItems = storedItems.filter {it.name.contains(searchText, true)} as ArrayList<ListItem>
					runOnUiThread {
						getRecyclerAdapter()?.updateItems(listItems, text)
						binding.apply {
							mimetypesFastscroller.beVisibleIf(listItems.isNotEmpty())
							mimetypesPlaceholder.beVisibleIf(listItems.isEmpty())
							mimetypesPlaceholder2.beGone()
						}
					}
				}
			}
		}
	}

	override fun setupDateTimeFormat() {}
	override fun setupFontSize() {}
	override fun toggleFilenameVisibility() {}
	override fun finishActMode() {}

	override fun columnCountChanged() {
		layoutManager.spanCount = fileColumnCnt
		getRecyclerAdapter()?.apply {notifyItemRangeChanged(0, listItems.size)}
	}

	private fun setupSearch(menu: Menu) {
		val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
		searchMenuItem = menu.findItem(R.id.search)
		(searchMenuItem!!.actionView as SearchView).apply {
			setSearchableInfo(searchManager.getSearchableInfo(componentName))
			isSubmitButtonEnabled = false
			queryHint = getString(org.fossify.commons.R.string.search)
			setOnQueryTextListener(object: SearchView.OnQueryTextListener {
				override fun onQueryTextSubmit(query: String) = false
				override fun onQueryTextChange(newText: String): Boolean {
					if(isSearchOpen) searchQueryChanged(newText)
					return true
				}
			})
		}

		MenuItemCompat.setOnActionExpandListener(searchMenuItem, object: MenuItemCompat.OnActionExpandListener {
			override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
				isSearchOpen = true
				lastSearchedText = ""
				return true
			}
			override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
				isSearchOpen = false
				lastSearchedText = ""
				searchQueryChanged("")
				return true
			}
		})
	}

	private fun getProperItems(callback: (ArrayList<ListItem>)->Unit) {
		val items = ArrayList<ListItem>()
		val showHidden = config.shouldShowHidden()
		val uri = MediaStore.Files.getContentUri(currentVolume)
		val projection = arrayOf(MediaStore.Files.FileColumns.MIME_TYPE, MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME,
			MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED)
		try {
			queryCursor(uri, projection) {cursor ->
				try {
					val fullMimetype = cursor.getStringValue(MediaStore.Files.FileColumns.MIME_TYPE)?.lowercase(Locale.getDefault())?:return@queryCursor
					val name = cursor.getStringValue(MediaStore.Files.FileColumns.DISPLAY_NAME)
					if(!showHidden && name.startsWith('.')) return@queryCursor
					val size = cursor.getLongValue(MediaStore.Files.FileColumns.SIZE)
					if(size == 0L) return@queryCursor

					val path = cursor.getStringValue(MediaStore.Files.FileColumns.DATA)
					val lastModified = cursor.getLongValue(MediaStore.Files.FileColumns.DATE_MODIFIED)*1000
					val mimetype = fullMimetype.substringBefore('/')
					if(when(currentMimeType) {
						IMAGES -> mimetype == "image"
						VIDEOS -> mimetype == "video"
						AUDIO -> mimetype == "audio" || extraAudioMimeTypes.contains(fullMimetype)
						DOCUMENTS -> mimetype == "text" || extraDocumentMimeTypes.contains(fullMimetype)
						ARCHIVES -> archiveMimeTypes.contains(fullMimetype)
						OTHERS -> mimetype != "image" && mimetype != "video" && mimetype != "audio" && mimetype != "text" && !extraAudioMimeTypes
							.contains(fullMimetype) && !extraDocumentMimeTypes.contains(fullMimetype) && !archiveMimeTypes.contains(fullMimetype)
						else -> false
					}) items.add(ListItem(this, path, name, false, 0, size, lastModified))
				} catch(_: Exception) {}
			}
		} catch(e: Throwable) {this.error(e)}
		callback(items)
	}

	private fun addItems(items: ArrayList<ListItem>) {
		ListItem.sorting = config.getFolderSorting(currentMimeType)
		items.sort()
		if(isDestroyed || isFinishing) return

		storedItems = items
		ItemsAdapter(this, storedItems, this, binding.mimetypesList, null, true, true).apply {
			setItemListZoom(zoomListener)
			binding.mimetypesList.adapter = this
		}
		if(areSystemAnimationsEnabled) binding.mimetypesList.scheduleLayoutAnimation()
		binding.mimetypesPlaceholder.beVisibleIf(items.isEmpty())
	}

	private fun getRecyclerAdapter() = binding.mimetypesList.adapter as? ItemsAdapter

	private fun showSortingDialog() {
		ChangeSortingDialog(this, currentMimeType) {recreateList()}
	}

	private fun changeViewType() {
		ChangeViewTypeDialog(this, currentMimeType, true) {
			recreateList()
			setupLayoutManager(config.getFolderViewType(currentMimeType))
		}
	}

	private fun reFetchItems() {
		fileColumnCnt = config.fileColumnCnt
		getProperItems {runOnUiThread {
			addItems(it)
			val viewType = config.getFolderViewType(currentMimeType)
			if(currentViewType != viewType) setupLayoutManager(viewType)
		}}
	}

	private fun recreateList() {
		val items = getRecyclerAdapter()?.listItems
		if(items != null) addItems(items as ArrayList<ListItem>)
	}

	private fun setupLayoutManager(viewType: Int) {
		if(viewType == VIEW_TYPE_GRID) setupGridLayoutManager()
		else setupListLayoutManager()
		currentViewType = viewType

		binding.mimetypesList.adapter = null
		initZoomListener()
		addItems(storedItems)
	}

	private fun setupGridLayoutManager() {
		layoutManager.spanCount = fileColumnCnt
		layoutManager.spanSizeLookup = object: GridLayoutManager.SpanSizeLookup() {
			override fun getSpanSize(pos: Int): Int {
				return if(getRecyclerAdapter()?.isSectionTitle(pos) == true) layoutManager.spanCount else 1
			}
		}
	}

	private fun setupListLayoutManager() {layoutManager.spanCount = 1}

	private fun incColCount(by: Int) {
		if(currentViewType == VIEW_TYPE_GRID) {
			config.fileColumnCnt += by
			columnCountChanged()
		}
		getRecyclerAdapter()?.finishActMode()
	}

	private fun initZoomListener() {
		zoomListener = if(currentViewType == VIEW_TYPE_GRID) {
			object: MyRecyclerView.MyZoomListener {
				override fun zoomIn() {
					if(layoutManager.spanCount > 1) incColCount(-1)
				}
				override fun zoomOut() {
					if(layoutManager.spanCount < MAX_COLUMN_COUNT) incColCount(1)
				}
			}
		} else null
	}
}