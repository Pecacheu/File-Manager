package org.fossify.filemanager.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.SAVE_DISCARD_PROMPT_INTERVAL
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyEditText
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityReadTextBinding
import org.fossify.filemanager.dialogs.SaveAsDialog
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.extensions.openPath
import java.io.File
import java.io.OutputStream

//TODO Remote?

class ReadTextActivity: SimpleActivity() {
	companion object {
		private const val SELECT_SAVE_FILE_INTENT = 1
		private const val SELECT_SAVE_FILE_AND_EXIT_INTENT = 2
	}

	private val binding by viewBinding(ActivityReadTextBinding::inflate)

	private var filePath = ""
	private var originalText = ""
	private var searchIndex = 0
	private var lastSavePromptTS = 0L
	private var searchMatches = emptyList<Int>()
	private var isSearchActive = false

	private lateinit var searchQueryET: MyEditText
	private lateinit var searchPrevBtn: ImageView
	private lateinit var searchNextBtn: ImageView
	private lateinit var searchClearBtn: ImageView

	override fun onCreate(savedInstanceState: Bundle?) {
		isMaterialActivity = true
		super.onCreate(savedInstanceState)
		setContentView(binding.root)
		setupOptionsMenu()
		binding.apply {
			updateMaterialActivityViews(readTextCoordinator, readTextView, useTransparentNavigation = true, useTopSearchMenu = false)
			setupMaterialScrollListener(readTextHolder, readTextToolbar)
		}
		searchQueryET = findViewById(org.fossify.commons.R.id.search_query)
		searchPrevBtn = findViewById(org.fossify.commons.R.id.search_previous)
		searchNextBtn = findViewById(org.fossify.commons.R.id.search_next)
		searchClearBtn = findViewById(org.fossify.commons.R.id.search_clear)
		if(checkAppSideloading()) return

		val uri = if(intent.extras?.containsKey(REAL_FILE_PATH) == true)
			Uri.fromFile(File(intent.extras?.get(REAL_FILE_PATH).toString()))
		else intent.data

		if(uri == null) {
			finish()
			return
		}

		val filename = getFilenameFromUri(uri)
		if(filename.isNotEmpty()) binding.readTextToolbar.title = Uri.decode(filename)
		binding.readTextView.onGlobalLayout {ensureBackgroundThread {checkIntent(uri)}}
		setupSearchButtons()
	}

	override fun onResume() {
		super.onResume()
		setupToolbar(binding.readTextToolbar, NavigationIcon.Arrow)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
		super.onActivityResult(requestCode, resultCode, resultData)
		if(requestCode == SELECT_SAVE_FILE_INTENT && resultCode == RESULT_OK && resultData != null && resultData.data != null) {
			val outputStream = contentResolver.openOutputStream(resultData.data!!)
			val selectedFilePath = getRealPathFromURI(intent.data!!)
			saveTextContent(outputStream, selectedFilePath == filePath)
		}
	}

	override fun onBackPressed() {
		val hasUnsavedChanges = originalText != binding.readTextView.text.toString()
		when {
			isSearchActive -> closeSearch()
			hasUnsavedChanges && System.currentTimeMillis() - lastSavePromptTS > SAVE_DISCARD_PROMPT_INTERVAL -> {
				lastSavePromptTS = System.currentTimeMillis()
				ConfirmationAdvancedDialog(this, "", org.fossify.commons.R.string.save_before_closing,
					org.fossify.commons.R.string.save, org.fossify.commons.R.string.discard) {
					if(it) saveText()
					else super.onBackPressed()
				}
			} else -> super.onBackPressed()
		}
	}

	private fun setupOptionsMenu() {
		binding.readTextToolbar.setOnMenuItemClickListener {menuItem ->
			when(menuItem.itemId) {
				R.id.menu_search -> openSearch()
				R.id.menu_save -> saveText()
				R.id.menu_open_with -> openPath(intent.dataString!!, true)
				R.id.menu_print -> printText()
				else -> return@setOnMenuItemClickListener false
			}
			return@setOnMenuItemClickListener true
		}
	}

	private fun openSearch() {
		isSearchActive = true
		binding.searchWrapper.beVisible()
		showKeyboard(searchQueryET)
		binding.readTextView.requestFocus()
		binding.readTextView.setSelection(0)
		searchQueryET.postDelayed({searchQueryET.requestFocus()}, 250)
	}

	private fun saveText() {
		if(filePath.isEmpty()) filePath = getRealPathFromURI(intent.data!!)?:""
		SaveAsDialog(this, filePath, true) {path, filename ->
			if(filePath.isEmpty()) {
				Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
					type = "text/plain"
					putExtra(Intent.EXTRA_TITLE, filename)
					addCategory(Intent.CATEGORY_OPENABLE)
					startActivityForResult(this, SELECT_SAVE_FILE_AND_EXIT_INTENT)
				}
			} else if(hasStoragePermission()) {
				val file = File(path)
				getFileOutputStream(file.toFileDirItem(this), true) {saveTextContent(it, path == filePath)}
			} else toast(org.fossify.commons.R.string.no_storage_permissions)
		}
	}

	private fun saveTextContent(outputStream: OutputStream?, shouldOverwriteOriginalText: Boolean) {
		if(outputStream != null) {
			val currentText = binding.readTextView.text.toString()
			outputStream.bufferedWriter().use {it.write(currentText)}
			toast(org.fossify.commons.R.string.file_saved)
			hideKeyboard()
			if(shouldOverwriteOriginalText) originalText = currentText
			super.onBackPressed()
		} else toast(org.fossify.commons.R.string.unknown_error_occurred)
	}

	private fun printText() {
		try {
			val webView = WebView(this)
			webView.webViewClient = object: WebViewClient() {
				override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
				override fun onPageFinished(view: WebView, url: String) {createWebPrintJob(view)}
			}
			webView.loadData(binding.readTextView.text.toString(), "text/plain", "UTF-8")
		} catch(e: Throwable) {error(e)}
	}

	private fun createWebPrintJob(webView: WebView) {
		val jobName = if(filePath.isNotEmpty()) filePath.getFilenameFromPath()
		else getString(R.string.app_name)
		val printAdapter = webView.createPrintDocumentAdapter(jobName)
		(getSystemService(PRINT_SERVICE) as? PrintManager)?.apply {
			print(jobName, printAdapter, PrintAttributes.Builder().build())
		}
	}

	private fun checkIntent(uri: Uri) {
		originalText = if(uri.scheme == "file") {
			filePath = uri.path!!
			val file = File(filePath)
			if(file.exists()) {
				try {file.readText()} catch(e: Throwable) {error(e); ""}
			} else {
				toast(org.fossify.commons.R.string.unknown_error_occurred); ""
			}
		} else {
			try {
				contentResolver.openInputStream(uri)!!.bufferedReader().use {it.readText()}
			} catch(e: Throwable) {
				this.error(e)
				if(e !is OutOfMemoryError) finish()
				return
			}
		}
		runOnUiThread {
			binding.readTextView.setText(originalText)
			showKeyboard(binding.readTextView)
		}
	}

	private fun setupSearchButtons() {
		searchQueryET.onTextChangeListener {searchTextChanged(it)}
		searchPrevBtn.setOnClickListener {goToPrevSearchResult()}
		searchNextBtn.setOnClickListener {goToNextSearchResult()}
		searchClearBtn.setOnClickListener {closeSearch()}
		searchQueryET.setOnEditorActionListener(TextView.OnEditorActionListener {_, actionId, _ ->
			if(actionId == EditorInfo.IME_ACTION_SEARCH) {
				searchNextBtn.performClick()
				return@OnEditorActionListener true
			}
			false
		})
		binding.searchWrapper.setBackgroundColor(getProperPrimaryColor())
		val contrastColor = getProperPrimaryColor().getContrastColor()
		arrayListOf(searchPrevBtn, searchNextBtn, searchClearBtn).forEach {it.applyColorFilter(contrastColor)}
	}

	private fun searchTextChanged(text: String) {
		binding.readTextView.text?.clearBackgroundSpans()
		if(text.isNotBlank()) {
			searchMatches = binding.readTextView.text.toString().searchMatches(text)
			binding.readTextView.highlightText(text, getProperPrimaryColor())
		}
		selectSearchMatch()
		searchQueryET.postDelayed({searchQueryET.requestFocus()}, 50)
	}

	private fun goToPrevSearchResult() {
		if(searchIndex > 0) searchIndex--
		else searchIndex = searchMatches.lastIndex
		selectSearchMatch()
	}
	private fun goToNextSearchResult() {
		if(searchIndex < searchMatches.lastIndex) searchIndex++
		else searchIndex = 0
		selectSearchMatch()
	}
	private fun closeSearch() {
		searchQueryET.text?.clear()
		isSearchActive = false
		binding.searchWrapper.beGone()
		hideKeyboard()
	}

	private fun selectSearchMatch() {
		if(searchMatches.isNotEmpty()) {
			binding.readTextView.requestFocus()
			binding.readTextView.setSelection(searchMatches.getOrNull(searchIndex)?:0)
		} else hideKeyboard()
	}
}