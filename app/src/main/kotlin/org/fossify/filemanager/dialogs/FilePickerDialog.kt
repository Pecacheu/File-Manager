package org.fossify.filemanager.dialogs

import android.os.Parcelable
import android.util.Log
import android.util.Xml
import android.view.KeyEvent
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.R
import org.fossify.commons.adapters.FilepickerFavoritesAdapter
import org.fossify.commons.adapters.FilepickerItemsAdapter
import org.fossify.commons.databinding.DialogFilepickerBinding
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.humanizePath
import org.fossify.filemanager.helpers.awaitBackgroundThread
import org.fossify.filemanager.models.DeviceType
import org.fossify.filemanager.models.ListItem

//import org.fossify.commons.views.Breadcrumbs
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
	private val breadcrumbs: Breadcrumbs
	private var mFirstUpdate = true
	private var mPrevPath = ""
	private var mScrollStates = HashMap<String, Parcelable>()
	private var mDialog: AlertDialog? = null
	private var showHidden = activity.config.shouldShowHidden()

	init {
		awaitBackgroundThread {
			if(!ListItem.dirExists(activity, currPath)) currPath = activity.config.getHome(currPath)
			if(currPath.startsWith(activity.filesDir.absolutePath)) currPath = activity.config.internalStoragePath
		}

		//TODO Fix this because it looks ugly
		//Replace Breadcrumbs
		binding.apply {
			val parser = activity.resources.getXml(org.fossify.filemanager.R.xml.search_view)
			val attr = Xml.asAttributeSet(parser)

			//filepickerBreadcrumbs.layoutParams
			var ob = filepickerBreadcrumbs
			val lp = ob.layoutParams
			//RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
			breadcrumbs = Breadcrumbs(activity, attr)
			Log.i("test", "idx: ${ob.paddingStart}, ${ob.paddingTop}, ${ob.paddingEnd}")
			//breadcrumbs.setPadding(ob.paddingStart, ob.paddingTop, ob.paddingEnd, ob.paddingBottom)
			breadcrumbs.setPadding(30, 30, 30, 30)
			filepickerFilesHolder.removeView(ob)
			filepickerFilesHolder.addView(breadcrumbs, 0, lp)
		}

		breadcrumbs.apply {
			listener = this@FilePickerDialog
			updateFontSize(activity.getTextSize(), false)
		}

		val builder = activity.getAlertDialogBuilder().setNegativeButton(R.string.cancel, null).setOnKeyListener {_, i, keyEv ->
			if(keyEv.action == KeyEvent.ACTION_UP && i == KeyEvent.KEYCODE_BACK) {
				val crumbs = breadcrumbs
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

		binding.filepickerFavoritesLabel.text = activity.getString(R.string.favorites)
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
			callback(it)
			mDialog?.dismiss()
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

	private fun updateItems(items: ArrayList<FileDirItem>) {
		if(!items.any {it.isDirectory} && !mFirstUpdate && !pickFile && !showFAB) {
			verifySel()
			return
		}
		val sortedItems = items.sortedWith(compareBy({!it.isDirectory}, {it.name.lowercase()}))
		val adapter = FilepickerItemsAdapter(activity, sortedItems, binding.filepickerList) {
			if((it as FileDirItem).isDirectory) {
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
			breadcrumbs.setBreadcrumb(currPath)
			if(root.context.areSystemAnimationsEnabled) filepickerList.scheduleLayoutAnimation()
			layoutManager.onRestoreInstanceState(mScrollStates[currPath.trimEnd('/')])
		}

		mFirstUpdate = false
		mPrevPath = currPath
	}

	private fun verifySel(fd: FileDirItem?=null) = ensureBackgroundThread {
		if(currPath != "/") currPath = currPath.trimEnd('/')
		val ok = if(pickFile) fd?.isDirectory == false else fd?.isDirectory != false
		if(ok) {
			callback(currPath)
			mDialog?.dismiss()
		} else {
			activity.toast(if(pickFile) org.fossify.filemanager.R.string.select_file
				else org.fossify.filemanager.R.string.select_folder)
		}
	}

	private fun getItems(path: String, callback: (ArrayList<FileDirItem>)->Unit) = ensureBackgroundThread {
		val dev = DeviceType.fromPath(activity, path)

		fun loadItems() {
			try {
				val items = ListItem.listDir(activity, path, false) {
					currPath != path || mDialog?.isShowing != true
				}
				if(items == null) {
					callback(ArrayList<FileDirItem>(0))
					return
				}
				//Send initial items asap, get proper child count asynchronously
				val fdItems = items.map {it.asFdItem()} as ArrayList<FileDirItem>
				callback(fdItems)
				if(dev.type != DeviceType.SAF && dev.type != DeviceType.OTG) getChildren(path, fdItems)
			} catch(e: Throwable) {
				activity.error(e)
				callback(ArrayList<FileDirItem>(0))
			}
		}

		if(dev.type == DeviceType.SAF) {
			activity.handleAndroidSAFDialog(path, true) {
				if(it) loadItems()
				else activity.toast(R.string.no_storage_permissions)
			}
		} else loadItems()
	}

	private fun getChildren(path: String, items: ArrayList<FileDirItem>) {
		for(fd in items.filter {it.isDirectory}) {
			if(currPath != path || mDialog?.isShowing != true) return
			val cnt = ListItem(activity, fd.path, fd.name, true, 0, 0, 0).getChildCount(showHidden)
			if(cnt != 0) updateChildCount(fd, cnt)
		}
	}
	private fun updateChildCount(item: FileDirItem, count: Int) = activity.runOnUiThread {
		(binding.filepickerList.adapter as FilepickerItemsAdapter).apply {
			val pos = fileDirItems.indexOf(item)
			if(pos == -1) return@apply
			item.children = count
			notifyItemChanged(pos, Unit)
		}
	}

	private fun setupFavorites() {
		val favs = activity.config.favorites.map {activity.humanizePath(it)}
		FilepickerFavoritesAdapter(activity, favs, binding.filepickerFavoritesList) {
			currPath = it as String
			verifySel()
		}.apply {
			binding.filepickerFavoritesList.adapter = this
		}
	}

	private fun showFavorites() {
		binding.apply {
			filepickerFavoritesHolder.beVisible()
			filepickerFilesHolder.beGone()
			val drawable = activity.resources.getColoredDrawableWithColor(R.drawable.ic_folder_vector,
				activity.getProperPrimaryColor().getContrastColor())
			filepickerFabShowFavorites.setImageDrawable(drawable)
		}
	}

	private fun hideFavorites() {
		binding.apply {
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
			val item = breadcrumbs.getItem(id)
			if(currPath != item.path.trimEnd('/')) {
				currPath = item.path
				tryUpdateItems()
			}
		}
	}
}