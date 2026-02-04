package org.fossify.filemanager.activities

import android.net.Uri
import android.os.Bundle
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.exception.ZipException.Type
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.LocalFileHeader
import org.fossify.commons.dialogs.EnterPasswordDialog
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.R
import org.fossify.filemanager.adapters.DecompressItemsAdapter
import org.fossify.filemanager.databinding.ActivityDecompressBinding
import org.fossify.filemanager.dialogs.FilePickerDialog
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.setLastModified
import org.fossify.filemanager.models.ListItem
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

class DecompressActivity: SimpleActivity() {
	companion object {
		const val PATH = "fm-path"
		private const val PASSWORD = "password"
	}

	private val binding by viewBinding(ActivityDecompressBinding::inflate)
	private val allFiles = ArrayList<ListItem>()
	private var currentPath = ""
	private var uri: Uri? = null
	private lateinit var path: String
	private var password: String? = null
	private var passwordDialog: EnterPasswordDialog? = null
	private var filename = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(binding.root)
		setupOptionsMenu()
		binding.apply {setupViews(decompressCoordinator, decompressList, decompressAppbar, decompressList)}

		uri = intent.data
		if(uri == null) {
			val sp = intent.getStringExtra(PATH)
			if(sp == null) {
				toast(org.fossify.commons.R.string.unknown_error_occurred)
				return
			}
			path = sp
		} else {
			path = getRealPathFromURI(uri!!)?:Uri.decode(uri.toString())
		}

		password = savedInstanceState?.getString(PASSWORD, null)
		filename = path.getFilenameFromPath()
		binding.decompressToolbar.title = filename
		setupFilesList()
	}

	override fun onResume() {
		super.onResume()
		setupTopAppBar(binding.decompressAppbar, NavigationIcon.Arrow)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(PASSWORD, password)
	}

	private fun setupOptionsMenu() {
		binding.decompressToolbar.setOnMenuItemClickListener {menuItem ->
			when(menuItem.itemId) {
				R.id.decompress -> decompressFiles()
				else -> return@setOnMenuItemClickListener false
			}
			return@setOnMenuItemClickListener true
		}
	}

	override fun onBackPressedCompat(): Boolean {
		return if(currentPath.isEmpty()) {
			false
		} else {
			val newPath = if(currentPath.contains('/')) currentPath.getParentPath() else ""
			updateCurrentPath(newPath)
			true
		}
	}

	private fun setupFilesList() {
		allFiles.clear()
		fillAllListItems {updateCurrentPath("")}
	}

	private fun updateCurrentPath(path: String) {
		currentPath = path
		try {
			val items = getFolderItems(currentPath)
			runOnUiThread {
				DecompressItemsAdapter(this, items, binding.decompressList) {
					if((it as ListItem).isDir) updateCurrentPath(it.path)
				}.apply {
					binding.decompressList.adapter = this
				}
			}
		} catch(e: Throwable) {error(e)}
	}

	private fun decompressFiles() {
		FilePickerDialog(this, path) {dest ->
			handleSAFDialog(dest) {if(it) decompressTo(dest)}
		}
	}

	private fun decompressTo(dest: String) = ensureBackgroundThread {
		config.reloadPath = true
		var inStream: InputStream? = null
		var zipStream: ZipInputStream? = null
		try {
			inStream = if(uri != null) contentResolver.openInputStream(uri!!)
				else ListItem.getInputStream(this, path)
			zipStream = ZipInputStream(BufferedInputStream(inStream))
			if(password != null) zipStream.setPassword(password?.toCharArray())
			val buffer = ByteArray(1024)
			while(true) {
				val entry = zipStream.nextEntry?:break
				val parent = "$dest/${filename.substringBeforeLast('.')}"
				val newPath = "$parent/${entry.fileName.trimEnd('/')}"

				ListItem.mkDir(this, parent, true)
				if(entry.isDirectory) continue
				//Check if vulnerable for ZIP path traversal
				val outFile = File(newPath)
				if(!outFile.canonicalPath.startsWith(parent)) continue //TODO Test if works with remote

				ListItem.getOutputStream(this, newPath).use {
					var count: Int
					while(true) {
						count = zipStream.read(buffer)
						if(count == -1) break
						it.write(buffer, 0, count)
					}
				}
				outFile.setLastModified(entry) //TODO Won't work with remote, needs fix
			}
			toast(R.string.decompression_successful)
			finish()
		} catch(e: Throwable) {
			error(e)
		} finally {
			zipStream?.close()
			inStream?.close()
		}
	}

	private fun getFolderItems(parent: String): ArrayList<ListItem> {
		return allFiles.filter {
			val fileParent = if(it.path.contains('/')) it.path.getParentPath() else ""
			fileParent == parent
		}.sortedWith(compareBy({!it.isDir}, {it.name})).toMutableList() as ArrayList<ListItem>
	}

	private fun fillAllListItems(callback: ()->Unit) = ensureBackgroundThread {
		var inStream: InputStream? = null
		var zipStream: ZipInputStream? = null
		try {
			inStream = if(uri != null) contentResolver.openInputStream(uri!!)
				else ListItem.getInputStream(this, path)
			zipStream = ZipInputStream(BufferedInputStream(inStream))
			if(password != null) zipStream.setPassword(password?.toCharArray())
			var zipEntry: LocalFileHeader?
			while(true) {
				try {
					zipEntry = zipStream.nextEntry
				} catch(e: ZipException) {
					if(e.type == Type.WRONG_PASSWORD) {
						if(password != null) {
							toast(org.fossify.commons.R.string.invalid_password)
							passwordDialog?.clearPassword()
						} else {
							runOnUiThread(::askForPassword)
						}
						return@ensureBackgroundThread
					} else break
				}
				if(zipEntry == null) break //Show progress bar only after password dialog is dismissed
				runOnUiThread {
					if(binding.progressIndicator.isGone()) binding.progressIndicator.show()
				}

				if(passwordDialog != null) {
					passwordDialog?.dismiss(false)
					passwordDialog = null
				}
				val fn = zipEntry.fileName.removeSuffix("/")
				allFiles.add(ListItem(this, fn, fn.getFilenameFromPath(), zipEntry.isDirectory, 0, 0L, zipEntry.lastModifiedTime))
			}
			callback()
		} catch(e: Throwable) {
			error(e)
		} finally {
			runOnUiThread {binding.progressIndicator.hide()}
			zipStream?.close()
			inStream?.close()
		}
	}

	private fun askForPassword() {
		passwordDialog = EnterPasswordDialog(this, callback = {newPassword ->
			password = newPassword
			setupFilesList()
		}, cancelCallback = {
			finish()
		})
	}
}