package org.fossify.filemanager.dialogs

import android.os.Parcelable
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.R
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.FilepickerItemsAdapter
import org.fossify.filemanager.databinding.DialogFilepickerBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.humanizePath
import org.fossify.filemanager.models.DeviceType
import org.fossify.filemanager.models.ListItem
import org.fossify.filemanager.views.Breadcrumbs

/**
 * The only filepicker constructor with a couple optional parameters
 *
 * @param activity has to be activity to avoid some Theme.AppCompat issues
 * @param currPath initial path of the dialog, defaults to the external storage
 * @param pickFile toggle used to determine if we are picking a file or a folder
 * @param showFAB toggle the displaying of a Floating Action Button for creating new folders
 * @param callback the callback used for returning the selected file/folder
 */
class FilePickerDialog(
	private val activity: SimpleActivity,
	private var currPath: String,
	private val pickFile: Boolean = false,
	private val showFAB: Boolean = true,
	private val callback: (pickedPath: String)->Unit
): Breadcrumbs.BreadcrumbsListener {
	private val binding = DialogFilepickerBinding.inflate(activity.layoutInflater)
	private var mFirstUpdate = true
	private var mPrevPath = ""
	private var mScrollStates = HashMap<String, Parcelable>()
	private var mDialog: AlertDialog? = null
	private var showHidden = activity.config.shouldShowHidden()

	init {
		ensureBackgroundThread {
			if(!ListItem.dirExists(activity, currPath)) currPath = activity.config.getHome(currPath)
			if(currPath.startsWith(activity.filesDir.absolutePath)) currPath = activity.config.internalStoragePath
			activity.runOnUiThread(::initDialog)
		}
	}

	fun initDialog() {
		binding.filepickerBreadcrumbs.apply {
			listener = this@FilePickerDialog
			updateFontSize(activity.getTextSize(), false)
		}

		val builder = activity.getAlertDialogBuilder().setNegativeButton(R.string.cancel, null).setOnKeyListener {_, i, keyEv ->
			if(keyEv.action == KeyEvent.ACTION_UP && i == KeyEvent.KEYCODE_BACK) {
				val crumbs = binding.filepickerBreadcrumbs
				if(crumbs.getItemCount() > 1) {
					crumbs.removeBreadcrumb()
					currPath = crumbs.getLastItem().path.trimEnd('/')
					tryUpdateItems()
				} else mDialog?.dismiss()
			}
			true
		}

		if(!pickFile) builder.setPositiveButton(R.string.ok, null)

		if(showFAB) {
			binding.filepickerFab.apply {
				beVisible()
				setOnClickListener {createNewFolder()}
			}
		}
		val secondaryFabBottomMargin = activity.resources.getDimension(if(showFAB)
			R.dimen.secondary_fab_bottom_margin else R.dimen.activity_margin).toInt()
		binding.filepickerFabsHolder.apply {
			(layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = secondaryFabBottomMargin
		}

		binding.filepickerPlaceholder.setTextColor(activity.getProperTextColor())
		binding.filepickerFastscroller.updateColors(activity.getProperPrimaryColor())
		binding.filepickerFabShowHidden.apply {
			beVisibleIf(!showHidden)
			setOnClickListener {
				activity.handleHiddenFolderPasswordProtection {
					beGone()
					showHidden = true
					tryUpdateItems()
				}
			}
		}

		binding.filepickerFabShowFavorites.apply {
			beVisibleIf(context.config.favorites.isNotEmpty())
			setOnClickListener {
				if(binding.filepickerFavoritesHolder.isVisible()) hideFavorites()
				else showFavorites()
			}
		}

		activity.setupDialogStuff(binding.root, builder, getTitle()) {mDialog = it}
		if(!pickFile) mDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {verifySel()}
		tryUpdateItems()
		setupFavorites()
	}

	private fun getTitle() = if(pickFile) R.string.select_file else R.string.select_folder
	private fun createNewFolder() {
		CreateNewItemDialog(activity, currPath, true) {
			currPath = it
			tryUpdateItems()
		}
	}

	private fun tryUpdateItems() {
		getItems(currPath) {
			activity.runOnUiThread {
				binding.filepickerPlaceholder.beGone()
				updateItems(it)
			}
		}
	}

	private fun updateItems(items: ArrayList<ListItem>) {
		if(!items.any {it.isDir} && !mFirstUpdate && !pickFile && !showFAB) {
			verifySel()
			return
		}
		val sortedItems = items.sortedWith(compareBy({!it.isDir}, {it.name.lowercase()}))
		val adapter = FilepickerItemsAdapter(activity, sortedItems, binding.filepickerList) {
			if((it as ListItem).isDir) {
				activity.handleLockedFolderOpening(it.path) {success ->
					if(success) {
						currPath = it.path
						tryUpdateItems()
					}
				}
			} else if(pickFile) verifySel(it)
		}
		val layoutManager = binding.filepickerList.layoutManager as LinearLayoutManager
		mScrollStates[mPrevPath.trimEnd('/')] = layoutManager.onSaveInstanceState()!!

		binding.apply {
			filepickerList.adapter = adapter
			binding.filepickerBreadcrumbs.setBreadcrumb(currPath)
			if(root.context.areSystemAnimationsEnabled) filepickerList.scheduleLayoutAnimation()
			layoutManager.onRestoreInstanceState(mScrollStates[currPath.trimEnd('/')])
		}

		mFirstUpdate = false
		mPrevPath = currPath
	}

	private fun verifySel(item: ListItem? = null) = ensureBackgroundThread {
		if(currPath != "/") currPath = currPath.trimEnd('/')
		if(item == null || item.isDir != pickFile) {
			mDialog?.dismiss()
			callback(item?.path?:currPath)
		} else {
			activity.toast(if(pickFile) org.fossify.filemanager.R.string.select_file
				else org.fossify.filemanager.R.string.select_folder)
		}
	}

	private fun getItems(path: String, callback: (ArrayList<ListItem>)->Unit) = ensureBackgroundThread {
		val dev = DeviceType.fromPath(activity, path)

		fun loadItems() {
			try {
				val items = ListItem.listDir(activity, path, false) {
					currPath != path || mDialog?.isShowing != true
				}
				if(items == null) {
					callback(ArrayList(0))
					return
				}
				//Send initial items asap, get proper child count asynchronously
				callback(items)
				if(dev.type != DeviceType.SAF && dev.type != DeviceType.OTG) getChildren(path, items)
			} catch(e: Throwable) {
				activity.error(e)
				callback(ArrayList(0))
			}
		}

		if(dev.type == DeviceType.SAF) {
			activity.handleAndroidSAFDialog(path, true) {
				if(it) loadItems()
				else activity.toast(R.string.no_storage_permissions)
			}
		} else loadItems()
	}

	private fun getChildren(path: String, items: ArrayList<ListItem>) {
		for(item in items.filter {it.isDir}) {
			if(currPath != path || mDialog?.isShowing != true) return
			val cnt = item.getChildCount(showHidden)
			updateChildCount(item, cnt)
		}
	}
	private fun updateChildCount(item: ListItem, count: Int) = activity.runOnUiThread {
		(binding.filepickerList.adapter as FilepickerItemsAdapter).apply {
			val pos = listItems.indexOf(item)
			if(pos == -1) return@apply
			item.children = count
			notifyItemChanged(pos, Unit)
		}
	}

	private fun setupFavorites() {
		val favs = activity.config.favorites.map {ListItem(activity, it, activity.humanizePath(it), true, -2, 0, 0)}
		FilepickerItemsAdapter(activity, favs, binding.filepickerFavoritesList) {
			currPath = (it as ListItem).path
			tryUpdateItems()
			hideFavorites()
		}.apply {
			binding.filepickerFavoritesList.adapter = this
		}
	}

	private fun showFavorites() {
		mDialog?.setTitle(R.string.favorites)
		binding.apply {
			filepickerFabShowHidden.beGone()
			filepickerFavoritesHolder.beVisible()
			filepickerFilesHolder.beGone()
			val drawable = activity.resources.getColoredDrawableWithColor(R.drawable.ic_folder_vector,
				activity.getProperPrimaryColor().getContrastColor())
			filepickerFabShowFavorites.setImageDrawable(drawable)
		}
	}

	private fun hideFavorites() {
		mDialog?.setTitle(getTitle())
		binding.apply {
			filepickerFabShowHidden.beVisibleIf(!showHidden)
			filepickerFavoritesHolder.beGone()
			filepickerFilesHolder.beVisible()
			val drawable = activity.resources.getColoredDrawableWithColor(R.drawable.ic_star_vector,
				activity.getProperPrimaryColor().getContrastColor())
			filepickerFabShowFavorites.setImageDrawable(drawable)
		}
	}

	override fun breadcrumbClicked(id: Int) {
		if(id == 0) {
			StoragePickerDialog(activity, currPath) {
				currPath = it
				tryUpdateItems()
			}
		} else {
			val item = binding.filepickerBreadcrumbs.getItem(id)
			if(currPath != item.path.trimEnd('/')) {
				currPath = item.path
				tryUpdateItems()
			}
		}
	}
}