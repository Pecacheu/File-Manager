package org.fossify.filemanager.fragments

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.ItemsFragmentBinding
import org.fossify.filemanager.dialogs.CreateNewItemDialog
import org.fossify.filemanager.dialogs.StoragePickerDialog
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.isPathOnRoot
import org.fossify.filemanager.extensions.isRemotePath
import org.fossify.filemanager.extensions.humanizePath
import org.fossify.filemanager.helpers.Remote
import org.fossify.filemanager.helpers.RootHelpers
import org.fossify.filemanager.models.ListItem
import org.fossify.filemanager.views.Breadcrumbs
import java.io.File

class ItemsFragment(context: Context, attributeSet: AttributeSet): MyViewPagerFragment<MyViewPagerFragment.ItemsInnerBinding>(context, attributeSet),
		Breadcrumbs.BreadcrumbsListener {
	private var showHidden = false
	private var scrollStates = HashMap<String, Parcelable>()
	private var storedItems = ArrayList<ListItem>()
	private var itemsIgnoringSearch = ArrayList<ListItem>()
	private lateinit var binding: ItemsFragmentBinding

	override fun onFinishInflate() {
		super.onFinishInflate()
		binding = ItemsFragmentBinding.bind(this)
		innerBinding = ItemsInnerBinding(binding)
	}

	override fun setupFragment(activity: SimpleActivity) {
		if(this.activity == null) {
			this.activity = activity
			binding.apply {
				breadcrumbs.listener = this@ItemsFragment
				itemsSwipeRefresh.setOnRefreshListener {refreshFragment()}
				itemsFab.setOnClickListener {
					if(isCreateDocumentIntent) (activity as MainActivity).createDocumentConfirmed(currentPath)
					else createNewItem()
				}
			}
		}
	}

	override fun onResume(textColor: Int) {
		context.updateTextColors(this)
		getRecyclerAdapter()?.apply {
			updatePrimaryColor()
			updateTextColor(textColor)
			initDrawables()
		}
		binding.apply {
			val properPrimaryColor = context.getProperPrimaryColor()
			itemsFastscroller.updateColors(properPrimaryColor)
			progressBar.setIndicatorColor(properPrimaryColor)
			progressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)

			if(currentPath != "") breadcrumbs.updateColor(textColor)
			itemsSwipeRefresh.isEnabled = lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false
		}
	}

	override fun setupFontSize() {
		getRecyclerAdapter()?.updateFontSizes()
		if(currentPath != "") binding.breadcrumbs.updateFontSize(context.getTextSize(), false)
	}

	override fun setupDateTimeFormat() {getRecyclerAdapter()?.updateDateTimeFormat()}
	override fun finishActMode() {getRecyclerAdapter()?.finishActMode()}

	fun openPath(path: String, forceRefresh: Boolean = false) {
		Log.i("test", "openPath '$path' $forceRefresh")
		if((activity as? BaseSimpleActivity)?.isAskingPermissions == true) return
		var realPath = path.trimEnd('/')
		if(realPath.isEmpty()) realPath = "/"

		scrollStates[currentPath] = getScrollState()!!
		currentPath = realPath
		showHidden = context.config.shouldShowHidden()
		showProgressBar()

		getItems(currentPath) {originalPath, listItems ->
			if(currentPath != originalPath) return@getItems
			FileDirItem.sorting = context.config.getFolderSorting(currentPath)
			listItems.sort()

			if(context.config.getFolderViewType(currentPath) == VIEW_TYPE_GRID && listItems.none {it.isSectionTitle}) {
				if(listItems.any {it.mIsDirectory} && listItems.any {!it.mIsDirectory}) {
					val firstFileIndex = listItems.indexOfFirst {!it.mIsDirectory}
					if(firstFileIndex != -1) {
						val sectionTitle = ListItem("", "", false, 0, 0, 0, false, true)
						listItems.add(firstFileIndex, sectionTitle)
					}
				}
			}

			itemsIgnoringSearch = listItems
			activity?.runOnUiThread {
				(activity as? MainActivity)?.refreshMenuItems()
				addItems(listItems, forceRefresh)
				if(context != null) {
					val viewType = context.config.getFolderViewType(currentPath)
					if(currentViewType != viewType) setupLayoutManager(viewType)
				}
				hideProgressBar()
			}
		}
	}

	private fun addItems(items: ArrayList<ListItem>, forceRefresh: Boolean = false) {
		activity?.runOnUiThread {
			binding.itemsSwipeRefresh.isRefreshing = false
			binding.breadcrumbs.setBreadcrumb(currentPath)
			if(!forceRefresh && items.hashCode() == storedItems.hashCode()) return@runOnUiThread

			storedItems = items
			if(binding.itemsList.adapter == null) {
				binding.breadcrumbs.updateFontSize(context.getTextSize(), true)
			}

			ItemsAdapter(activity as SimpleActivity, storedItems, this, binding.itemsList, isPickMultipleIntent, binding.itemsSwipeRefresh) {
				if((it as? ListItem)?.isSectionTitle == true) {
					openDirectory(it.mPath)
					searchClosed()
				} else {
					itemClicked(it as FileDirItem)
				}
			}.apply {
				setItemListZoom(zoomListener)
				binding.itemsList.adapter = this
			}

			if(context.areSystemAnimationsEnabled) binding.itemsList.scheduleLayoutAnimation()
			getRecyclerLayoutManager().onRestoreInstanceState(scrollStates[currentPath])
		}
	}

	private fun getScrollState() = getRecyclerLayoutManager().onSaveInstanceState()
	private fun getRecyclerLayoutManager() = (binding.itemsList.layoutManager as MyGridLayoutManager)

	private fun getItems(path: String, callback: (originalPath: String, items: ArrayList<ListItem>)->Unit) {
		ensureBackgroundThread {
			if(activity?.isDestroyed == false && activity?.isFinishing == false) {
				val conf = context.config
				if(isRemotePath(path)) getRemoteItemsOf(path, callback)
				else if(context.isRestrictedSAFOnlyRoot(path)) {
					activity?.runOnUiThread {hideProgressBar()}
					activity?.handleAndroidSAFDialog(path, openInSystemAppAllowed = true) {
						if(!it) {
							activity?.toast(org.fossify.commons.R.string.no_storage_permissions)
							return@handleAndroidSAFDialog
						}
						val getFileSize = conf.getFolderSorting(currentPath) and SORT_BY_SIZE != 0
						context.getAndroidSAFFileItems(path, conf.shouldShowHidden(), getFileSize) {fileItems ->
							callback(path, getListItemsFromFileDirItems(fileItems))
						}
					}
				} else if(context.isPathOnOTG(path) && conf.OTGTreeUri.isNotEmpty()) {
					val getFileSize = conf.getFolderSorting(currentPath) and SORT_BY_SIZE != 0
					context.getOTGItems(path, conf.shouldShowHidden(), getFileSize) {
						callback(path, getListItemsFromFileDirItems(it))
					}
				} else if(!conf.enableRootAccess || !context.isPathOnRoot(path)) {
					getRegularItemsOf(path, callback)
				} else {
					RootHelpers(activity!!).getFiles(path, callback)
				}
			}
		}
	}

	private fun getRemoteItemsOf(path: String, callback: (originalPath: String, items: ArrayList<ListItem>)->Unit) {
		val shouldGetCnt = context.config.getFolderViewType(currentPath) == VIEW_TYPE_LIST
		try {
			val r = context.config.getRemoteForPath(path)
			if(r == null) throw Error(context.getString(R.string.no_remote_err))
			r.connect()
			val items = r.listDir(path, context.config.shouldShowHidden())
			callback(path, items)
			if(shouldGetCnt) getChildCount(items)
		} catch(e: Throwable) {
			Remote.err(activity!!, e)
			callback(path, ArrayList<ListItem>())
		}
	}

	private fun getRegularItemsOf(path: String, callback: (originalPath: String, items: ArrayList<ListItem>)->Unit) {
		val items = ArrayList<ListItem>()
		val files = File(path).listFiles()?.filterNotNull()
		if(context == null || files == null) {
			callback(path, items)
			return
		}

		val isSortingBySize = context.config.getFolderSorting(currentPath) and SORT_BY_SIZE != 0
		val shouldGetCnt = context.config.getFolderViewType(currentPath) == VIEW_TYPE_LIST
		val lastModifieds = context.getFolderLastModifieds(path)

		for(file in files) {
			val listItem = getListItemFromFile(file, isSortingBySize, lastModifieds)
			if(listItem != null && wantedMimeTypes.any {isProperMimeType(it, file.absolutePath, file.isDirectory)}) {
				items.add(listItem)
			}
		}

		//Send out initial item list asap, get proper child count asynchronously
		callback(path, items)
		if(shouldGetCnt) getChildCount(items)
	}

	private fun getChildCount(items: ArrayList<ListItem>) {
		items.filter {it.mIsDirectory}.forEach {
			if(context != null) {
				val cnt = it.getChildCount(activity!!, showHidden)
				if(cnt != 0) activity?.runOnUiThread {getRecyclerAdapter()?.updateChildCount(it.mPath, cnt)}
			}
		}
	}

	private fun getListItemFromFile(file: File, isSortingBySize: Boolean, lastModifieds: HashMap<String, Long>): ListItem? {
		val curPath = file.absolutePath
		val curName = file.name
		if(!showHidden && curName.startsWith(".")) return null

		var lastModified = lastModifieds.remove(curPath)
		val isDirectory = if(lastModified != null) false else file.isDirectory
		val size = if(isDirectory) {if(isSortingBySize) file.getProperSize(showHidden) else 0L} else file.length()
		if(lastModified == null) lastModified = file.lastModified()
		return ListItem(curPath, curName, isDirectory, -1, size, lastModified, false, false)
	}

	private fun getListItemsFromFileDirItems(fileDirItems: ArrayList<FileDirItem>): ArrayList<ListItem> {
		val listItems = ArrayList<ListItem>()
		fileDirItems.forEach {
			val listItem = ListItem(it.path, it.name, it.isDirectory, it.children, it.size, it.modified, false, false)
			if(wantedMimeTypes.any {mimeType -> isProperMimeType(mimeType, it.path, it.isDirectory)}) {
				listItems.add(listItem)
			}
		}
		return listItems
	}

	private fun itemClicked(item: FileDirItem) {
		if(item.isDirectory) openDirectory(item.path)
		else clickedPath(item.path)
	}

	private fun openDirectory(path: String) {
		(activity as? MainActivity)?.apply {openedDirectory()}
		openPath(path)
	}

	override fun searchQueryChanged(text: String) {
		lastSearchedText = text
		if(context == null) return
		binding.apply {
			itemsSwipeRefresh.isEnabled = text.isEmpty() && activity?.config?.enablePullToRefresh != false
			when {
				text.isEmpty() -> {
					itemsFastscroller.beVisible()
					getRecyclerAdapter()?.updateItems(itemsIgnoringSearch)
					itemsPlaceholder.beGone()
					itemsPlaceholder2.beGone()
					hideProgressBar()
				} text.length == 1 -> {
					itemsFastscroller.beGone()
					itemsPlaceholder.beVisible()
					itemsPlaceholder2.beVisible()
					hideProgressBar()
				} else -> {
					showProgressBar()
					ensureBackgroundThread {
						val files = searchFiles(text, currentPath)
						files.sortBy {it.getParentPath()}
						if(lastSearchedText != text) return@ensureBackgroundThread
						val listItems = ArrayList<ListItem>()
						var previousParent = ""
						files.forEach {
							val parent = it.mPath.getParentPath()
							if(!it.isDirectory && parent != previousParent && context != null) {
								val sectionTitle = ListItem(parent, context.humanizePath(parent), false, 0, 0, 0, true, false)
								listItems.add(sectionTitle)
								previousParent = parent
							}
							if(it.isDirectory) {
								val sectionTitle = ListItem(it.path, context.humanizePath(it.path), true, 0, 0, 0, true, false)
								listItems.add(sectionTitle)
								previousParent = parent
							}
							if(!it.isDirectory) listItems.add(it)
						}

						activity?.runOnUiThread {
							getRecyclerAdapter()?.updateItems(listItems, text)
							itemsFastscroller.beVisibleIf(listItems.isNotEmpty())
							itemsPlaceholder.beVisibleIf(listItems.isEmpty())
							itemsPlaceholder2.beGone()
							hideProgressBar()
						}
					}
				}
			}
		}
	}

	private fun searchFiles(text: String, path: String): ArrayList<ListItem> {
		val files = ArrayList<ListItem>()
		if(context == null) return files

		val sorting = context.config.getFolderSorting(path)
		FileDirItem.sorting = context.config.getFolderSorting(currentPath)
		val isSortingBySize = sorting and SORT_BY_SIZE != 0
		File(path).listFiles()?.sortedBy {it.isDirectory}?.forEach {
			if(!showHidden && it.isHidden) return@forEach
			if(it.isDirectory) {
				if(it.name.contains(text, true)) {
					val fileDirItem = getListItemFromFile(it, isSortingBySize, HashMap())
					if(fileDirItem != null) files.add(fileDirItem)
				}
				files.addAll(searchFiles(text, it.absolutePath))
			} else {
				if(it.name.contains(text, true)) {
					val fileDirItem = getListItemFromFile(it, isSortingBySize, HashMap())
					if(fileDirItem != null) files.add(fileDirItem)
				}
			}
		}
		return files
	}

	private fun searchClosed() {
		binding.apply {
			lastSearchedText = ""
			itemsSwipeRefresh.isEnabled = activity?.config?.enablePullToRefresh != false
			itemsFastscroller.beVisible()
			itemsPlaceholder.beGone()
			itemsPlaceholder2.beGone()
			hideProgressBar()
		}
	}

	private fun createNewItem() {
		CreateNewItemDialog(activity as SimpleActivity, currentPath) {
			if(it) refreshFragment()
			else activity?.toast(org.fossify.commons.R.string.unknown_error_occurred)
		}
	}

	override fun getRecyclerAdapter() = binding.itemsList.adapter as? ItemsAdapter

	private fun setupLayoutManager(viewType: Int) {
		if(viewType == VIEW_TYPE_GRID) setupGridLayoutManager()
		else setupListLayoutManager()
		currentViewType = viewType

		binding.itemsList.adapter = null
		initZoomListener(binding.itemsList.layoutManager as MyGridLayoutManager)
		addItems(storedItems, true)
	}

	private fun setupGridLayoutManager() {
		val layoutManager = binding.itemsList.layoutManager as MyGridLayoutManager
		layoutManager.spanCount = context?.config?.fileColumnCnt?:3
		layoutManager.spanSizeLookup = object: GridLayoutManager.SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int {
				return if(getRecyclerAdapter()?.isASectionTitle(position) == true ||
					getRecyclerAdapter()?.isGridTypeDivider(position) == true) layoutManager.spanCount else 1
			}
		}
	}

	private fun setupListLayoutManager() {
		val layoutManager = binding.itemsList.layoutManager as MyGridLayoutManager
		layoutManager.spanCount = 1
	}

	override fun columnCountChanged() {
		(binding.itemsList.layoutManager as MyGridLayoutManager).spanCount = context.config.fileColumnCnt
		(activity as? MainActivity)?.refreshMenuItems()
		getRecyclerAdapter()?.apply {notifyItemRangeChanged(0, listItems.size)}
	}

	private fun showProgressBar() {
		binding.progressBar.beVisible()
		binding.progressBar.show()
	}
	private fun hideProgressBar() {binding.progressBar.hide()}

	fun getBreadcrumbs() = binding.breadcrumbs
	override fun toggleFilenameVisibility() {getRecyclerAdapter()?.updateDisplayFilenamesInGrid()}

	override fun breadcrumbClicked(id: Int) {
		if(id == 0) {
			StoragePickerDialog(activity as SimpleActivity, currentPath, context.config.enableRootAccess) {
				getRecyclerAdapter()?.finishActMode()
				openPath(it)
			}
		} else {
			val item = binding.breadcrumbs.getItem(id)
			openPath(item.path)
		}
	}

	override fun refreshFragment() {openPath(currentPath)}

	override fun deleteFiles(files: ArrayList<FileDirItem>) {
		val hasFolder = files.any {it.isDirectory}
		handleFileDeleting(files, hasFolder)
	}

	override fun selectedPaths(paths: ArrayList<String>) {
		(activity as MainActivity).pickedPaths(paths)
	}
}