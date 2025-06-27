package org.fossify.filemanager.fragments

import android.content.Context
import android.os.Parcelable
import android.os.Process
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.ItemsFragmentBinding
import org.fossify.filemanager.dialogs.CreateNewItemDialog
import org.fossify.filemanager.dialogs.StoragePickerDialog
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.humanizePath
import org.fossify.filemanager.extensions.isNotFoundErr
import org.fossify.filemanager.helpers.REMOTE_URI
import org.fossify.filemanager.models.DeviceType
import org.fossify.filemanager.models.ListItem
import org.fossify.filemanager.views.Breadcrumbs

class ItemsFragment(context: Context, attributeSet: AttributeSet): MyViewPagerFragment<MyViewPagerFragment.ItemsInnerBinding>(context, attributeSet),
		Breadcrumbs.BreadcrumbsListener {
	private var showHidden = false
	private var scrollStates = HashMap<String, Parcelable>()
	private var storedItems = ArrayList<ListItem>()
	private var itemsIgnoringSearch = ArrayList<ListItem>()
	private lateinit var binding: ItemsFragmentBinding
	private var hasProgress = false

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
				itemsSwipeRefresh.setOnRefreshListener(::refreshFragment)
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

			if(currentPath.isNotEmpty()) breadcrumbs.updateColor(textColor)
			itemsSwipeRefresh.isEnabled = lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false
		}
	}

	override fun setupFontSize() {
		getRecyclerAdapter()?.updateFontSizes()
		if(currentPath.isNotEmpty()) binding.breadcrumbs.updateFontSize(context.getTextSize(), false)
	}

	override fun setupDateTimeFormat() {getRecyclerAdapter()?.updateDateTimeFormat()}
	override fun finishActMode() {getRecyclerAdapter()?.finishActMode()}

	fun openPath(path: String, forceRefresh: Boolean=false) {
		Log.i("test", "openPath $path")
		if(activity?.isAskingPermissions == true) return
		var realPath = path.trimEnd('/')
		if(realPath.isEmpty()) realPath = "/"

		scrollStates[currentPath] = getScrollState()!!
		currentPath = realPath
		showHidden = context.config.shouldShowHidden()
		showProgressBar()

		ListItem.sorting = context.config.getFolderSorting(currentPath)
		getItems(currentPath) {origPath, unsorted ->
			if(currentPath != origPath) return@getItems
			val items = unsorted.filter {itm -> wantedMimeTypes.any {isProperMimeType(it, itm.path, itm.isDir)}} as ArrayList<ListItem>
			items.sort()

			if(context.config.getFolderViewType(currentPath) == VIEW_TYPE_GRID && items.none {it.isSectionTitle}) {
				if(items.any {it.isDir} && items.any {!it.isDir}) {
					val firstFileIndex = items.indexOfFirst {!it.isDir}
					if(firstFileIndex != -1) items.add(firstFileIndex, ListItem.gridDivider())
				}
			}

			itemsIgnoringSearch = items
			activity?.runOnUiThread {
				(activity as? MainActivity)?.refreshMenuItems()
				addItems(items, forceRefresh)
				if(context != null) {
					val viewType = context.config.getFolderViewType(currentPath)
					if(currentViewType != viewType) setupLayoutManager(viewType)
				}
				hideProgressBar()
			}
		}
	}

	private fun addItems(items: ArrayList<ListItem>, forceRefresh: Boolean=false) {
		activity?.runOnUiThread {
			binding.itemsSwipeRefresh.isRefreshing = false
			binding.breadcrumbs.setBreadcrumb(currentPath)
			if(!forceRefresh && items.hashCode() == storedItems.hashCode()) return@runOnUiThread

			storedItems = items
			if(binding.itemsList.adapter == null) {
				binding.breadcrumbs.updateFontSize(context.getTextSize(), true)
			}

			ItemsAdapter(activity!!, storedItems, this, binding.itemsList, binding.itemsSwipeRefresh, true, true) {
				if(it.isDir || it.isSectionTitle) {
					openDirectory(it.path)
					searchClosed()
					return@ItemsAdapter true
				}
				return@ItemsAdapter false
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
		val dev = DeviceType.fromPath(context, path)
		val isList = context.config.getFolderViewType(currentPath) == VIEW_TYPE_LIST

		fun loadItems() {
			try {
				val items = ListItem.listDir(activity!!, path, false) {
					context == null || currentPath != path
				}
				if(items == null) {
					callback(path, ArrayList<ListItem>(0))
					return
				}
				if(dev.type == DeviceType.DEV) {
					val lastMods = context.getFolderLastModifieds(path)
					for(li in items) li.modified = lastMods.remove(li.path)?:0
				}
				//Send initial items asap, get proper child count asynchronously
				callback(path, items)
				if(isList && dev.type != DeviceType.SAF && dev.type != DeviceType.OTG) getChildren(path, items)
			} catch(e: Throwable) {
				if(isNotFoundErr(e) && path == context?.config?.getHome(path)) {
					activity?.error(e, context.getString(R.string.reset_home)) {
						if(!it) return@error
						val home = if(dev.type == DeviceType.REMOTE) "$REMOTE_URI${dev.id}:"
							else context.config.internalStoragePath
						context.config.setHome(home)
						openPath(home)
					}
				} else activity?.error(e)
				callback(path, ArrayList<ListItem>(0))
			}
		}

		ensureBackgroundThread {
			if(activity?.isDestroyed == false && activity?.isFinishing == false) {
				if(dev.type == DeviceType.SAF) {
					activity?.handleAndroidSAFDialog(path, true) {
						if(it) loadItems() else {
							hideProgressBar()
							activity?.toast(org.fossify.commons.R.string.no_storage_permissions)
						}
					}
				} else loadItems()
			}
		}
	}

	private fun getChildren(path: String, items: ArrayList<ListItem>) {
		for(li in items.filter {it.isDir}) {
			if(context == null || currentPath != path) return
			val cnt = li.getChildCount(showHidden)
			activity?.runOnUiThread {getRecyclerAdapter()?.updateChildCount(li, cnt)}
		}
	}

	private fun openDirectory(path: String) {
		(activity as? MainActivity)?.openedDirectory()
		openPath(path)
	}

	override fun searchQueryChanged(text: String) {
		Log.i("test", "searchQueryChanged '$text'")
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
					itemsPlaceholder2.beGone()
					ensureBackgroundThread {
						Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
						var files: ArrayList<ListItem>?
						try {
							ListItem.sorting = context.config.getFolderSorting(currentPath)
							files = ListItem.listDir(activity!!, currentPath, true, text) {
								context == null || lastSearchedText != text
							}?:return@ensureBackgroundThread
							files.sortBy {it.path.getParentPath()}
						} catch(e: Throwable) {
							activity?.error(e)
							files = null
						}
						val items = ArrayList<ListItem>(files?.size?:0)
						if(files != null) {
							var prevParent = ""
							for(li in files) {
								val parent = li.path.getParentPath()
								if(!li.isDir && parent != prevParent && context != null) {
									items.add(ListItem.sectionTitle(parent, context.humanizePath(parent)))
									prevParent = parent
								}
								if(li.isDir) {
									items.add(ListItem.sectionTitle(li.path, context.humanizePath(li.path)))
									prevParent = parent
								} else items.add(li)
							}
						}
						activity?.runOnUiThread {
							getRecyclerAdapter()?.updateItems(items, text)
							itemsFastscroller.beVisibleIf(items.isNotEmpty())
							itemsPlaceholder.beVisibleIf(items.isEmpty())
							hideProgressBar()
						}
					}
				}
			}
		}
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
		CreateNewItemDialog(activity as SimpleActivity, currentPath) {refreshFragment()}
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
			override fun getSpanSize(pos: Int): Int {
				return if(getRecyclerAdapter()?.isSectionTitle(pos) == true ||
					getRecyclerAdapter()?.isGridDivider(pos) == true) layoutManager.spanCount else 1
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
		hasProgress = true
	}
	private fun hideProgressBar(force: Boolean=true) {
		if(force) hasProgress = false else if(hasProgress) return
		binding.progressBar.hide()
		if(binding.progressBar.isAnimating) postDelayed({hideProgressBar(false)}, 50)
	}

	fun getBreadcrumbs() = binding.breadcrumbs
	override fun toggleFilenameVisibility() {getRecyclerAdapter()?.updateDisplayFilenamesInGrid()}
	override fun refreshFragment() = openPath(currentPath)

	override fun breadcrumbClicked(id: Int) {
		if(id == 0) {
			StoragePickerDialog(activity as SimpleActivity, currentPath) {
				getRecyclerAdapter()?.finishActMode()
				openPath(it)
			}
		} else openPath(binding.breadcrumbs.getItem(id).path)
	}
}