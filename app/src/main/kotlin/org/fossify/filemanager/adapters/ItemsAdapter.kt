package org.fossify.filemanager.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.stericson.RootTools.RootTools
import org.fossify.filemanager.views.ItemsList
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.LocalFileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.dialogs.RenameDialog
import org.fossify.commons.dialogs.RenameItemDialog
import org.fossify.commons.dialogs.RenameItemsDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.deleteFile
import org.fossify.commons.extensions.deleteFileBg
import org.fossify.commons.extensions.deleteFolderBg
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAndroidSAFFileItems
import org.fossify.commons.extensions.getAndroidSAFUri
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getDefaultCopyDestinationPath
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileCount
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getFileOutputStreamSync
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperSize
import org.fossify.commons.extensions.getProperStatusBarColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleDeletePasswordProtection
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.isAudioFast
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.relativizeWith
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.getFilePlaceholderDrawables
import org.fossify.commons.interfaces.MyActionModeCallback
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.DecompressActivity
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.activities.SplashActivity
import org.fossify.filemanager.databinding.ItemDirGridBinding
import org.fossify.filemanager.databinding.ItemEmptyBinding
import org.fossify.filemanager.databinding.ItemFileDirListBinding
import org.fossify.filemanager.databinding.ItemFileGridBinding
import org.fossify.filemanager.databinding.ItemSectionBinding
import org.fossify.filemanager.dialogs.CompressAsDialog
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.isPathOnRoot
import org.fossify.filemanager.extensions.isRemotePath
import org.fossify.filemanager.extensions.isZipFile
import org.fossify.filemanager.extensions.setAs
import org.fossify.filemanager.extensions.sharePaths
import org.fossify.filemanager.extensions.tryOpenPathIntent
import org.fossify.filemanager.fragments.FavoritesFragment
import org.fossify.filemanager.fragments.ItemsFragment
import org.fossify.filemanager.fragments.MyViewPagerFragment
import org.fossify.filemanager.helpers.OPEN_AS_AUDIO
import org.fossify.filemanager.helpers.OPEN_AS_IMAGE
import org.fossify.filemanager.helpers.OPEN_AS_OTHER
import org.fossify.filemanager.helpers.OPEN_AS_TEXT
import org.fossify.filemanager.helpers.OPEN_AS_VIDEO
import org.fossify.filemanager.helpers.RootHelpers
import org.fossify.filemanager.interfaces.ItemOperationsListener
import org.fossify.filemanager.models.ListItem
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.util.LinkedList
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class ItemsAdapter(
	val activity: SimpleActivity,
	var listItems: MutableList<ListItem>,
	private val listener: ItemOperationsListener,
	val recyclerView: MyRecyclerView,
	private val swipeRefreshLayout: SwipeRefreshLayout?,
	canHaveIndividualViewType: Boolean = true,
	isMainActMode: Boolean = false,
	val itemClick: ((ListItem)->Boolean)? = null
): RecyclerView.Adapter<ItemsAdapter.ViewHolder>(), RecyclerViewFastScroller.OnPopupTextUpdate {
	private val isPickMultipleIntent = (listener as? MyViewPagerFragment<*>)?.isPickMultipleIntent == true
	private val selected = ArrayList<ListItem>()
	private lateinit var fileDrawable: Drawable
	private lateinit var folderDrawable: Drawable
	private var fileDrawables = HashMap<String, Drawable>()
	private var currentItemsHash = listItems.hashCode()
	private var textToHighlight = ""
	private var fontSize = 0f
	private var smallerFontSize = 0f
	private var dateFormat = ""
	private var timeFormat = ""

	private val config = activity.config
	private val viewType = if(canHaveIndividualViewType)
		config.getFolderViewType(listItems.firstOrNull {!it.isSectionTitle}?.path?.getParentPath()?:"")
		else config.viewType
	private val isListViewType = viewType == VIEW_TYPE_LIST
	private var displayFilenamesInGrid = config.displayFilenames

	companion object {
		private const val TYPE_FILE = 1
		private const val TYPE_DIR = 2
		private const val TYPE_SECTION = 3
		private const val TYPE_GRID_TYPE_DIVIDER = 4
	}

	fun setItemListZoom(zoomListener: MyRecyclerView.MyZoomListener?) {
		(recyclerView as ItemsList).setZoomListener(zoomListener, swipeRefreshLayout)
	}

	private fun prepareActionMode(menu: Menu) {
		val isFav = listener is FavoritesFragment
		menu.apply {
			findItem(R.id.cab_compress).isVisible = !isFav
			findItem(R.id.cab_decompress).isVisible = isOneFileSelected() && selected.first().name.isZipFile()
			findItem(R.id.cab_confirm_selection).isVisible = isPickMultipleIntent
			findItem(R.id.cab_copy_path).isVisible = isOneItemSelected()
			findItem(R.id.cab_open_with).isVisible = isOneFileSelected()
			findItem(R.id.cab_open_as).isVisible = isOneFileSelected()
			findItem(R.id.cab_set_as).isVisible = isOneFileSelected()
			findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected()
			findItem(R.id.cab_delete).isVisible = !isFav
			findItem(R.id.cab_rem_fav).isVisible = isFav
			checkHideBtnVisibility(this, isFav || isOnRemote())
		}
	}

	private fun isOnRemote(): Boolean {
		if(listener !is ItemsFragment || selected.isEmpty()) return false
		return isRemotePath(selected.first().path)
	}

	private fun actionItemPressed(id: Int) {
		if(selected.isEmpty()) return
		when(id) {
			R.id.cab_confirm_selection -> confirmSelection() //TODO Fix for remote
			R.id.cab_rename -> displayRenameDialog() //TODO Fix for remote
			R.id.cab_properties -> showProperties() //TODO Fix for remote
			R.id.cab_share -> shareFiles()
			R.id.cab_hide -> setHidden(true)
			R.id.cab_unhide -> setHidden(false)
			R.id.cab_create_shortcut -> createShortcut()
			R.id.cab_copy_path -> copyPath()
			R.id.cab_set_as -> setAs()
			R.id.cab_open_with -> openWith()
			R.id.cab_open_as -> openAs()
			R.id.cab_copy_to -> copyMoveTo(true)
			R.id.cab_move_to -> copyMoveTo(false)
			R.id.cab_compress -> compress() //TODO Fix for remote
			R.id.cab_decompress -> decompress(selected.first())
			R.id.cab_select_all -> selectAll()
			R.id.cab_delete -> askConfirmDelete()
			R.id.cab_rem_fav -> removeFav()
		}
	}

	private fun onActionModeCreated() {
		swipeRefreshLayout?.isRefreshing = false
		swipeRefreshLayout?.isEnabled = false
		(recyclerView as? ItemsList)?.zoomEnabled = false
	}

	private fun onActionModeDestroyed() {
		swipeRefreshLayout?.isEnabled = true
		(recyclerView as? ItemsList)?.zoomEnabled = true
	}

	override fun getItemViewType(position: Int): Int {
		return when {
			listItems[position].isGridDivider -> TYPE_GRID_TYPE_DIVIDER
			listItems[position].isSectionTitle -> TYPE_SECTION
			listItems[position].isDir -> TYPE_DIR
			else -> TYPE_FILE
		}
	}

	private fun removeFav() {
		for(f in selected) config.removeFavorite(f.path)
		(activity as? MainActivity)?.updateFavsList()
	}

	fun selectAll() {
		if(!actModeCallback.isSelectable) activity.startActionMode(actModeCallback)
		val cnt = itemCount - positionOffset
		for(i in 0 until cnt) setSelected(true, i, false)
		lastLongPressedItem = -1
		updateTitle()
	}

	fun isActMode() = selected.isNotEmpty()
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = Binding.getByItemViewType(viewType, isListViewType).inflate(layoutInflater, parent, false)
		return createViewHolder(binding.root)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = listItems[position]
		holder.bindView(item, true, !item.isSectionTitle) {view ->
			val viewType = getItemViewType(position)
			setupView(Binding.getByItemViewType(viewType, isListViewType).bind(view), item)
		}
		bindViewHolder(holder)
	}

	override fun getItemCount() = listItems.size
	private fun getSelectableItemCount() = listItems.filter {!it.isSectionTitle && !it.isGridDivider}.size
	private fun isOneFileSelected() = isOneItemSelected() && selected.first().isDir == false
	private fun isOneItemSelected() = selected.size == 1
	private fun firstSharePath() = selected.first().sharePath()

	private fun checkHideBtnVisibility(menu: Menu, noShow: Boolean) {
		var hiddenCnt = 0
		var unhiddenCnt = 0
		if(!noShow) selected.forEach {
			if(it.isHidden) hiddenCnt++ else unhiddenCnt++
		}
		menu.findItem(R.id.cab_hide).isVisible = unhiddenCnt > 0
		menu.findItem(R.id.cab_unhide).isVisible = hiddenCnt > 0
	}

	private fun confirmSelection() {
		val paths = selected.asSequence().filter {!it.isDir}.map {it.path}.toMutableList() as ArrayList<String>
		if(paths.isEmpty()) finishActMode()
		else listener.selectedPaths(paths)
	}

	fun displayRenameDialog() {
		val items = selected
		val paths = items.asSequence().map {it.path}.toMutableList() as ArrayList<String>
		when {
			paths.size == 1 -> {
				val oldPath = paths.first()
				//TODO moveFavorite should be part of rename file func, and should account for remote dest
				RenameItemDialog(activity, oldPath) {
					config.moveFavorite(oldPath, it)
					activity.runOnUiThread {
						listener.refreshFragment()
						(activity as? MainActivity)?.updateFavsList()
						finishActMode()
					}
				}
			}
			items.any {it.isDir} -> RenameItemsDialog(activity, paths) {
				activity.runOnUiThread {
					listener.refreshFragment()
					finishActMode()
				}
			} else -> RenameDialog(activity, paths, false) {
				activity.runOnUiThread {
					listener.refreshFragment()
					finishActMode()
				}
			}
		}
	}

	//TODO Is share path needed?
	fun showProperties() {
		if(selected.size <= 1) {
			PropertiesDialog(activity, firstSharePath(), config.shouldShowHidden())
		} else {
			val paths = selected.map {it.path}
			PropertiesDialog(activity, paths, config.shouldShowHidden())
		}
	}

	fun shareFiles() {
		ensureBackgroundThread {
			try {
				val paths = ArrayList<String>(selected.size)
				for(f in selected) addFileUris(f, paths)
				activity.sharePaths(paths)
			} catch(e: Throwable) {
				activity.error(e)
			}
		}
	}

	private fun setHidden(hide: Boolean) {
		ensureBackgroundThread {
			try {
				for(f in selected) f.setHidden(hide)
				activity.runOnUiThread {
					listener.refreshFragment()
					finishActMode()
				}
			} catch(e: Throwable) {
				activity.error(e)
			}
		}
	}

	@SuppressLint("UseCompatLoadingForDrawables")
	private fun createShortcut() {
		val manager = activity.getSystemService(ShortcutManager::class.java)
		if(manager.isRequestPinShortcutSupported) {
			val item = selected.first()
			val drawable = resources.getDrawable(R.drawable.shortcut_folder, null).mutate()
			getShortcutImage(item, drawable) {
				val i = Intent(activity, SplashActivity::class.java)
				i.action = Intent.ACTION_VIEW
				i.flags = i.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
				i.data = Uri.fromFile(File(item.path))

				val shortcut = ShortcutInfo.Builder(activity, item.path)
					.setShortLabel(item.name)
					.setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
					.setIntent(i)
					.build()

				manager.requestPinShortcut(shortcut, null)
			}
		}
	}

	private fun getShortcutImage(item: ListItem, drawable: Drawable, callback: ()->Unit) {
		val appIconColor = activity.baseConfig.appIconColor
		(drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_folder_background).applyColorFilter(appIconColor)
		if(item.isDir) callback()
		else ensureBackgroundThread {
			val options = RequestOptions().format(DecodeFormat.PREFER_ARGB_8888).skipMemoryCache(true)
				.diskCacheStrategy(DiskCacheStrategy.RESOURCE).fitCenter()
			val size = activity.resources.getDimension(org.fossify.commons.R.dimen.shortcut_size).toInt()
			val builder = Glide.with(activity).load(item.previewPath()).apply(options).centerCrop().submit(size, size)
			try {
				val bitmap = builder.get()
				drawable.findDrawableByLayerId(R.id.shortcut_folder_background).applyColorFilter(0)
				drawable.setDrawableByLayerId(R.id.shortcut_folder_image, bitmap)
			} catch(_: Throwable) {
				val fileIcon = fileDrawables.getOrElse(item.path.substringAfterLast('.').lowercase(Locale.getDefault())) {fileDrawable}
				drawable.setDrawableByLayerId(R.id.shortcut_folder_image, fileIcon)
			}
			activity.runOnUiThread {callback()}
		}
	}

	private fun addFileUris(itm: ListItem, paths: ArrayList<String>) {
		if(itm.isDir) {
			val dir = ListItem.listDir(activity, itm.path, true) {recyclerView.context == null}
			if(dir != null) for(li in dir) if(!li.isDir) paths.add(li.sharePath())
		} else paths.add(itm.sharePath())
	}

	private fun copyPath() {
		activity.copyToClipboard(firstSharePath())
		finishActMode()
	}

	private fun setAs() = activity.setAs(firstSharePath())
	private fun openWith() = activity.tryOpenPathIntent(firstSharePath(), true)

	private fun openAs() {
		val res = activity.resources
		val items = arrayListOf(RadioItem(OPEN_AS_TEXT, res.getString(R.string.text_file)), RadioItem(OPEN_AS_IMAGE, res.getString(R.string.image_file)),
			RadioItem(OPEN_AS_AUDIO, res.getString(R.string.audio_file)), RadioItem(OPEN_AS_VIDEO, res.getString(R.string.video_file)),
			RadioItem(OPEN_AS_OTHER, res.getString(R.string.other_file)))
		RadioGroupDialog(activity, items) {activity.tryOpenPathIntent(firstSharePath(), false, it as Int)}
	}

	fun copyMoveTo(isCopy: Boolean, confirmed: Boolean = false) {
		if(!isCopy && !confirmed) {
			activity.handleDeletePasswordProtection {copyMoveTo(false,true)}
			return
		}
		//TODO Fix FilePickerDialog
		/*val files = selected
		val firstFile = files[0]
		val source = firstFile.path.getParentPath()
		FilePickerDialog(activity = activity, currPath = activity.getDefaultCopyDestinationPath(config.shouldShowHidden(), source), pickFile = false,
			showHidden = config.shouldShowHidden(), showFAB = true, canAddShowHiddenButton = true, showFavoritesButton = true) {
			config.lastCopyPath = it
			if(activity.isPathOnRoot(it) || activity.isPathOnRoot(firstFile.path)) {
				copyMoveRootItems(files, it, isCopyOperation)
			} else {
				activity.copyMoveFilesTo(files, source, it, isCopyOperation, false, config.shouldShowHidden()) {
					if(!isCopyOperation) {
						files.forEach {sourceFileDir ->
							val sourcePath = sourceFileDir.path
							if(activity.isRestrictedSAFOnlyRoot(sourcePath) && activity.getDoesFilePathExist(sourcePath)) {
								activity.deleteFile(sourceFileDir, true) {
									listener.refreshFragment()
									activity.runOnUiThread {finishActMode()}
								}
							} else {
								val sourceFile = File(sourcePath)
								if(activity.getDoesFilePathExist(source) && ListItem.dirExists(activity, source) && sourceFile.list()
										?.isEmpty() == true && sourceFile.getProperSize(true) == 0L && sourceFile.getFileCount(true) == 0) {
									val sourceFolder = sourceFile.toFileDirItem(activity)
									activity.deleteFile(sourceFolder, true) {
										listener.refreshFragment()
										activity.runOnUiThread {finishActMode()}
									}
								} else {
									listener.refreshFragment()
									finishActMode()
								}
							}
						}
					} else {
						listener.refreshFragment()
						finishActMode()
					}
				}
			}
		}*/
	}

	/*private fun copyMoveRootItems(files: ArrayList<FileDirItem>, destinationPath: String, isCopyOperation: Boolean) {
		activity.toast(org.fossify.commons.R.string.copying)
		ensureBackgroundThread {
			val fileCnt = files.size
			RootHelpers(activity).copyMoveFiles(files, destinationPath, isCopyOperation) {
				when(it) {
					fileCnt -> activity.toast(org.fossify.commons.R.string.copying_success)
					0 -> activity.toast(org.fossify.commons.R.string.copy_failed)
					else -> activity.toast(org.fossify.commons.R.string.copying_success_partial)
				}
				activity.runOnUiThread {
					listener.refreshFragment()
					finishActMode()
				}
			}
		}
	}*/

	private fun decompress(li: ListItem) {
		val i = Intent(activity, DecompressActivity::class.java)
		i.putExtra(DecompressActivity.PATH, li.path)
		activity.startActivity(i)
	}

	private fun compress() {
		val firstPath = selected.first().path
		handleSAF {
			CompressAsDialog(activity, firstPath) {dest, pwd ->
				activity.toast(R.string.compressing)
				val paths = selected.map {it.path}
				ensureBackgroundThread {
					if(compressPaths(paths, dest, pwd)) {
						activity.runOnUiThread {
							activity.toast(R.string.compression_successful)
							listener.refreshFragment()
							finishActMode()
						}
					} else activity.toast(R.string.compressing_failed)
				}
			}
		}
	}

	private fun compressPaths(sourcePaths: List<String>, targetPath: String, password: String? = null): Boolean {
		val queue = LinkedList<String>()
		var res: Closeable? = null

		fun zipEntry(name: String) = ZipParameters().also {
			it.fileNameInZip = name
			if(password != null) {
				it.isEncryptFiles = true
				it.encryptionMethod = EncryptionMethod.AES
			}
		}
		try {
			val fos = ListItem.getOutputStream(activity, targetPath)
			res = fos
			val zout = password?.let {ZipOutputStream(fos, password.toCharArray())}?:ZipOutputStream(fos)

			sourcePaths.forEach {currPath ->
				var name: String
				var mainPath = currPath
				val base = "${mainPath.getParentPath()}/"
				res = zout
				queue.push(mainPath)
				if(ListItem.dirExists(activity, mainPath)) {
					name = "${mainPath.getFilenameFromPath()}/"
					zout.putNextEntry(ZipParameters().also {
						it.fileNameInZip = name
					})
				}
				while(!queue.isEmpty()) {
					mainPath = queue.pop()
					if(ListItem.dirExists(activity, mainPath)) {
						if(activity.isRestrictedSAFOnlyRoot(mainPath)) {
							activity.getAndroidSAFFileItems(mainPath, true) {files ->
								for(file in files) {
									name = file.path.relativizeWith(base)
									if(ListItem.dirExists(activity, file.path)) {
										queue.push(file.path)
										name = "${name.trimEnd('/')}/"
										zout.putNextEntry(zipEntry(name))
									} else {
										zout.putNextEntry(zipEntry(name))
										ListItem.getInputStream(activity, file.path).use {it.copyTo(zout)}
										zout.closeEntry()
									}
								}
							}
						} else {
							val mainFile = File(mainPath) //TODO USE listDir
							for(file in mainFile.listFiles()!!) {
								name = file.path.relativizeWith(base)
								if(ListItem.dirExists(activity, file.absolutePath)) {
									queue.push(file.absolutePath)
									name = "${name.trimEnd('/')}/"
									zout.putNextEntry(zipEntry(name))
								} else {
									zout.putNextEntry(zipEntry(name))
									ListItem.getInputStream(activity, file.path).use {it.copyTo(zout)}
									zout.closeEntry()
								}
							}
						}
					} else {
						name = if(base == currPath) currPath.getFilenameFromPath() else mainPath.relativizeWith(base)
						zout.putNextEntry(zipEntry(name))
						ListItem.getInputStream(activity, mainPath).use {it.copyTo(zout)}
						zout.closeEntry()
					}
				}
			}
		} catch(e: Throwable) {
			activity.error(e)
			return false
		} finally {
			res?.close()
		}
		return true
	}

	private fun askConfirmDelete() {
		if(config.skipDeleteConfirmation) deleteFiles()
		else activity.handleDeletePasswordProtection {
			val itemsCnt = selected.size
			val str = if(itemsCnt == 1) "\"${selected.first().name}\""
				else resources.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
			val question = String.format(resources.getString(org.fossify.commons.R.string.deletion_confirmation), str)
			ConfirmationDialog(activity, question) {deleteFiles()}
		}
	}

	private fun deleteFiles() {
		handleSAF {
			ensureBackgroundThread {
				try {
					for(f in selected) f.delete()
					activity.runOnUiThread {
						if(listener !is FavoritesFragment) listener.refreshFragment()
						(activity as? MainActivity)?.updateFavsList()
					}
				} catch(e: Throwable) {
					activity.error(e)
				}
			}
		}
	}

	private fun handleSAF(cb: ()->Unit) {
		val safPath = selected.first().path
		if(!isRemotePath(safPath) && activity.isPathOnRoot(safPath) && !RootTools.isRootAvailable()) {
			activity.toast(R.string.rooted_device_only)
			return
		}
		activity.handleSAFDialog(safPath) {if(it) cb()}
	}

	fun updateItems(newItems: ArrayList<ListItem>, highlightText: String = "") {
		if(newItems.hashCode() != currentItemsHash) {
			currentItemsHash = newItems.hashCode()
			textToHighlight = highlightText
			listItems = newItems
			notifyDataSetChanged()
			finishActMode()
		} else if(textToHighlight != highlightText) {
			textToHighlight = highlightText
			notifyDataSetChanged()
		}
	}

	fun updateFontSizes() {
		fontSize = activity.getTextSize()
		smallerFontSize = fontSize*0.8f
		notifyDataSetChanged()
	}

	fun updateDateTimeFormat() {
		dateFormat = config.dateFormat
		timeFormat = activity.getTimeFormat()
		notifyDataSetChanged()
	}

	fun updateDisplayFilenamesInGrid() {
		displayFilenamesInGrid = config.displayFilenames
		notifyDataSetChanged()
	}

	fun updateChildCount(item: ListItem, count: Int) {
		val pos = listItems.indexOf(item)
		if(pos == -1) return
		item.children = count
		notifyItemChanged(pos, Unit)
	}

	fun isSectionTitle(pos: Int) = listItems.getOrNull(pos)?.isSectionTitle == true
	fun isGridDivider(pos: Int) = listItems.getOrNull(pos)?.isGridDivider == true

	override fun onViewRecycled(holder: ViewHolder) {
		super.onViewRecycled(holder)
		if(!activity.isDestroyed && !activity.isFinishing) {
			val icon = Binding.getByItemViewType(holder.itemViewType, isListViewType).bind(holder.itemView).itemIcon
			if(icon != null) Glide.with(activity).clear(icon)
		}
	}

	private fun setupView(binding: ItemViewBinding, item: ListItem) {
		val isSelected = selected.contains(item)
		binding.apply {
			if(item.isSectionTitle) {
				itemIcon?.setImageDrawable(folderDrawable)
				itemSection?.text = if(textToHighlight.isEmpty()) item.name else item.name.highlightTextPart(textToHighlight, properPrimaryColor)
				itemSection?.setTextColor(textColor)
				itemSection?.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
			} else if(!item.isGridDivider) {
				root.setupViewBackground(activity)
				itemFrame.isSelected = isSelected
				itemName?.text = if(textToHighlight.isEmpty()) item.name else item.name.highlightTextPart(textToHighlight, properPrimaryColor)
				itemName?.setTextColor(textColor)
				itemName?.setTextSize(TypedValue.COMPLEX_UNIT_PX, if(isListViewType) fontSize else smallerFontSize)

				itemDetails?.setTextColor(textColor)
				itemDetails?.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

				itemDate?.setTextColor(textColor)
				itemDate?.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallerFontSize)

				itemCheck?.beVisibleIf(isSelected)
				if(isSelected) {
					itemCheck?.background?.applyColorFilter(properPrimaryColor)
					itemCheck?.applyColorFilter(contrastColor)
				}

				if(!isListViewType && !item.isDir) itemName?.beVisibleIf(displayFilenamesInGrid)
				else itemName?.beVisible()

				if(item.isDir) {
					itemIcon?.setImageDrawable(folderDrawable)
					if(item.children == -2) itemDetails?.beGone()
					else {
						itemDetails?.text = if(item.children == -1) ""
							else activity.resources.getQuantityString(org.fossify.commons.R.plurals.items, item.children, item.children)
						itemDetails?.beVisible()
					}
					itemDate?.beGone()
				} else {
					itemDetails?.text = item.size.formatSize()
					itemDate?.beVisible()
					itemDate?.text = item.modified.formatDate(activity, dateFormat, timeFormat)

					val drawable = fileDrawables.getOrElse(item.name.substringAfterLast('.').lowercase(Locale.getDefault())) {fileDrawable}
					val opts = RequestOptions().signature(item.getKey())
						.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
						.error(drawable)
						.transform(CenterCrop(), RoundedCorners(10))

					val imgPath = item.previewPath()
					if(!activity.isDestroyed && itemIcon != null) {
						Glide.with(activity).load(imgPath)
							.transition(DrawableTransitionOptions.withCrossFade())
							.apply(opts).into(itemIcon!!)
					}
				}
			}
		}
	}

	@SuppressLint("UseCompatLoadingForDrawables")
	fun initDrawables() {
		folderDrawable = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_folder_vector, properPrimaryColor)
		folderDrawable.alpha = 180
		fileDrawable = resources.getDrawable(org.fossify.commons.R.drawable.ic_file_generic, null)
		fileDrawables = getFilePlaceholderDrawables(activity)
	}

	override fun onChange(position: Int) = listItems.getOrNull(position)?.getBubbleText(activity, dateFormat, timeFormat)?:""

	private sealed interface Binding {
		companion object {
			fun getByItemViewType(viewType: Int, isListViewType: Boolean): Binding {
				return when(viewType) {
					TYPE_SECTION -> ItemSection
					TYPE_GRID_TYPE_DIVIDER -> ItemEmpty
					else -> {
						if(isListViewType) ItemFileDirList
						else if(viewType == TYPE_DIR) ItemDirGrid
						else ItemFileGrid
					}
				}
			}
		}

		fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding
		fun bind(view: View): ItemViewBinding

		data object ItemSection: Binding {
			override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
				return ItemSectionBindingAdapter(ItemSectionBinding.inflate(layoutInflater, viewGroup, attachToRoot))
			}
			override fun bind(view: View): ItemViewBinding {
				return ItemSectionBindingAdapter(ItemSectionBinding.bind(view))
			}
		}

		data object ItemEmpty: Binding {
			override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
				return ItemEmptyBindingAdapter(ItemEmptyBinding.inflate(layoutInflater, viewGroup, attachToRoot))
			}
			override fun bind(view: View): ItemViewBinding {
				return ItemEmptyBindingAdapter(ItemEmptyBinding.bind(view))
			}
		}

		data object ItemFileDirList: Binding {
			override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
				return ItemFileDirListBindingAdapter(ItemFileDirListBinding.inflate(layoutInflater, viewGroup, attachToRoot))
			}
			override fun bind(view: View): ItemViewBinding {
				return ItemFileDirListBindingAdapter(ItemFileDirListBinding.bind(view))
			}
		}

		data object ItemDirGrid: Binding {
			override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
				return ItemDirGridBindingAdapter(ItemDirGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
			}
			override fun bind(view: View): ItemViewBinding {
				return ItemDirGridBindingAdapter(ItemDirGridBinding.bind(view))
			}
		}

		data object ItemFileGrid: Binding {
			override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
				return ItemFileGridBindingAdapter(ItemFileGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
			}
			override fun bind(view: View): ItemViewBinding {
				return ItemFileGridBindingAdapter(ItemFileGridBinding.bind(view))
			}
		}
	}

	private interface ItemViewBinding: ViewBinding {
		val itemFrame: FrameLayout
		val itemName: TextView?
		val itemIcon: ImageView?
		val itemCheck: ImageView?
		val itemDetails: TextView?
		val itemDate: TextView?
		val itemSection: TextView?
	}

	private class ItemSectionBindingAdapter(val binding: ItemSectionBinding): ItemViewBinding {
		override val itemFrame: FrameLayout = binding.itemFrame
		override val itemName: TextView? = null
		override val itemIcon: ImageView = binding.itemIcon
		override val itemDetails: TextView? = null
		override val itemDate: TextView? = null
		override val itemCheck: ImageView? = null
		override val itemSection: TextView = binding.itemSection
		override fun getRoot(): View = binding.root
	}

	private class ItemEmptyBindingAdapter(val binding: ItemEmptyBinding): ItemViewBinding {
		override val itemFrame: FrameLayout = binding.itemFrame
		override val itemName: TextView? = null
		override val itemIcon: ImageView? = null
		override val itemDetails: TextView? = null
		override val itemDate: TextView? = null
		override val itemCheck: ImageView? = null
		override val itemSection: TextView? = null
		override fun getRoot(): View = binding.root
	}

	private class ItemFileDirListBindingAdapter(val binding: ItemFileDirListBinding): ItemViewBinding {
		override val itemFrame: FrameLayout = binding.itemFrame
		override val itemName: TextView = binding.itemName
		override val itemIcon: ImageView = binding.itemIcon
		override val itemDetails: TextView = binding.itemDetails
		override val itemDate: TextView = binding.itemDate
		override val itemCheck: ImageView? = null
		override val itemSection: TextView? = null
		override fun getRoot(): View = binding.root
	}

	private class ItemDirGridBindingAdapter(val binding: ItemDirGridBinding): ItemViewBinding {
		override val itemFrame: FrameLayout = binding.itemFrame
		override val itemName: TextView = binding.itemName
		override val itemIcon: ImageView = binding.itemIcon
		override val itemDetails: TextView? = null
		override val itemDate: TextView? = null
		override val itemCheck: ImageView = binding.itemCheck
		override val itemSection: TextView? = null
		override fun getRoot(): View = binding.root
	}

	private class ItemFileGridBindingAdapter(val binding: ItemFileGridBinding): ItemViewBinding {
		override val itemFrame: FrameLayout = binding.itemFrame
		override val itemName: TextView = binding.itemName
		override val itemIcon: ImageView = binding.itemIcon
		override val itemDetails: TextView? = null
		override val itemDate: TextView? = null
		override val itemCheck: ImageView? = binding.itemCheck
		override val itemSection: TextView? = null
		override fun getRoot(): View = binding.root
	}

	//---------------------------- From MyRecyclerViewAdapter ----------------------------

	private val resources = activity.resources!!
	private val layoutInflater = activity.layoutInflater
	private var textColor = activity.getProperTextColor()
	private var properPrimaryColor = activity.getProperPrimaryColor()
	private var contrastColor = properPrimaryColor.getContrastColor()
	private var actModeCallback: MyActionModeCallback
	private var positionOffset = 0
	private var actMode: ActionMode? = null
	private var actBarTextView: TextView? = null
	private var lastLongPressedItem = -1

	init {
		actModeCallback = object: MyActionModeCallback() {
			private var savedStatusBarColor = activity.getProperStatusBarColor()
			override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
				actionItemPressed(item.itemId)
				return true
			}

			override fun onCreateActionMode(actionMode: ActionMode, menu: Menu?): Boolean {
				selected.clear()
				isSelectable = true
				actMode = actionMode
				//if(isMainActMode) {
					actBarTextView = layoutInflater.inflate(org.fossify.commons.R.layout.actionbar_title, null) as TextView
					actBarTextView!!.layoutParams = ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
					actMode!!.customView = actBarTextView
					actBarTextView!!.setOnClickListener {
						if(getSelectableItemCount() == selected.size) finishActMode()
						else selectAll()
					}

					activity.menuInflater.inflate(R.menu.cab, menu)
					val bgColor = if(activity.isDynamicTheme()) resources.getColor(org.fossify.commons.R.color.you_contextual_status_bar_color, activity.theme)
					else resources.getColor(org.fossify.commons.R.color.dark_grey, activity.theme)

					savedStatusBarColor = activity.window.statusBarColor
					activity.animateStatusBarColor(bgColor, savedStatusBarColor, 300L)

					actBarTextView!!.setTextColor(bgColor.getContrastColor())
					activity.updateMenuItemColors(menu, baseColor = bgColor)

					if(activity.isDynamicTheme()) {
						actBarTextView?.onGlobalLayout {
							val backArrow = activity.findViewById<ImageView>(androidx.appcompat.R.id.action_mode_close_button)
							backArrow?.applyColorFilter(bgColor.getContrastColor())
						}
					}
				//}
				onActionModeCreated()
				return true
			}

			override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean {
				/*if(isMainActMode)*/ prepareActionMode(menu)
				return true
			}

			override fun onDestroyActionMode(actionMode: ActionMode) {
				isSelectable = false
				for(f in selected.clone() as ArrayList<ListItem>) {
					val pos = listItems.indexOf(f)
					if(pos != -1) setSelected(false, pos, false)
				}
				//if(isMainActMode) {
					activity.animateStatusBarColor(savedStatusBarColor, activity.window.statusBarColor, 400L)
					updateTitle()
					actBarTextView?.text = ""
					actMode = null
				//}
				lastLongPressedItem = -1
				selected.clear()
				onActionModeDestroyed()
			}
		}
		setupDragListener()
		initDrawables()
		updateFontSizes()
		dateFormat = config.dateFormat
		timeFormat = activity.getTimeFormat()
	}

	private fun setSelected(sel: Boolean, pos: Int, updateTitle: Boolean = true) {
		val item = listItems[pos]
		if((sel && selected.contains(item)) || (!sel && !selected.contains(item))) return
		if(sel) selected.add(item) else selected.remove(item)
		notifyItemChanged(pos + positionOffset)

		if(updateTitle) updateTitle()
		if(selected.isEmpty()) finishActMode()
	}

	private fun updateTitle() {
		val oldTitle = actBarTextView?.text
		val newTitle = "${selected.size} / ${getSelectableItemCount()}"
		if(oldTitle != newTitle) {
			actBarTextView?.text = newTitle
			actMode?.invalidate()
		}
	}

	private fun itemLongClicked(position: Int) {
		recyclerView.setDragSelectActive(position)
		lastLongPressedItem = if(lastLongPressedItem == -1) {
			position
		} else {
			val min = min(lastLongPressedItem, position)
			val max = max(lastLongPressedItem, position)
			for(i in min..max) setSelected(true, i, false)
			updateTitle()
			position
		}
	}

	private fun setupDragListener() {
		recyclerView.setupDragListener(object: MyRecyclerView.MyDragListener {
			override fun selectItem(pos: Int) {setSelected(true, pos)}
			override fun selectRange(initialSelection: Int, lastDraggedIndex: Int, minReached: Int, maxReached: Int) {
				selectItemRange(initialSelection, 0.coerceAtLeast(lastDraggedIndex - positionOffset),
					0.coerceAtLeast(minReached - positionOffset), maxReached - positionOffset)
				if(minReached != maxReached) lastLongPressedItem = -1
			}
		})
	}

	private fun selectItemRange(from: Int, to: Int, min: Int, max: Int) {
		if(from == to) {
			(min..max).filter {it != from}.forEach {setSelected(false, it)}
			return
		}
		if(to < from) {
			for(i in to..from) setSelected(true, i, true)
			if(min > -1 && min < to) (min until to).filter {it != from}.forEach {setSelected(false, it, true)}
			if(max > -1) for(i in from + 1..max) setSelected(false, i)
		} else {
			for(i in from..to) setSelected(true, i, true)
			if(max > -1 && max > to) (to + 1..max).filter {it != from}.forEach {setSelected(false, it, true)}
			if(min > -1) for(i in min until from) setSelected(false, i)
		}
	}

	fun finishActMode() = actMode?.finish()

	fun updateTextColor(textColor: Int) {
		this.textColor = textColor
		notifyDataSetChanged()
	}

	fun updatePrimaryColor() {
		properPrimaryColor = activity.getProperPrimaryColor()
		contrastColor = properPrimaryColor.getContrastColor()
	}

	private fun createViewHolder(view: View): ViewHolder {
		return ViewHolder(view)
	}

	private fun bindViewHolder(holder: ViewHolder) {
		holder.itemView.tag = holder
	}

	//TODO Fix for remote
	private fun openItem(item: ListItem) {
		val pager = listener as? MyViewPagerFragment<*>
		if(pager?.isGetContentIntent == true || pager?.isCreateDocumentIntent == true) {
			(activity as MainActivity).pickedPath(item.path)
		} else if(pager?.isGetRingtonePicker == true) {
			if(item.name.isAudioFast()) (activity as MainActivity).pickedRingtone(item.path)
			else activity.toast(R.string.select_audio_file)
		} else if(item.name.isZipFile()) decompress(item)
		else activity.tryOpenPathIntent(item.path, false)
	}

	open inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
		fun bindView(any: Any, allowSingleClick: Boolean, allowLongClick: Boolean, callback: (view: View)->Unit): View {
			return itemView.apply {
				callback(this)
				if(allowSingleClick) {
					setOnClickListener {viewClicked(any)}
					setOnLongClickListener {if(allowLongClick) viewLongClicked() else viewClicked(any); true}
				} else {
					setOnClickListener(null)
					setOnLongClickListener(null)
				}
			}
		}

		fun viewClicked(any: Any) {
			if(actModeCallback.isSelectable) {
				val pos = absoluteAdapterPosition - positionOffset
				val isSelected = selected.contains(listItems[pos])
				setSelected(!isSelected, pos)
			} else if(itemClick?.invoke(any as ListItem) != true) {
				openItem(any as ListItem)
			}
			lastLongPressedItem = -1
		}

		fun viewLongClicked() {
			val pos = absoluteAdapterPosition - positionOffset
			if(!actModeCallback.isSelectable) activity.startActionMode(actModeCallback)
			setSelected(true, pos)
			itemLongClicked(pos)
		}
	}
}