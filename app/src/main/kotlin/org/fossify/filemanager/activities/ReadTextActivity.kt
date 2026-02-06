package org.fossify.filemanager.activities

import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
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
import org.fossify.filemanager.extensions.launchPath
import org.fossify.filemanager.models.ListItem

class ReadTextActivity: SimpleActivity() {
	companion object {
		private const val KEY_UNSAVED_TEXT = "KEY_UNSAVED_TEXT"
	}

	private val binding by viewBinding(ActivityReadTextBinding::inflate)

	private var uri: Uri? = null
	private var path: String? = null
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
		super.onCreate(savedInstanceState)
		setContentView(binding.root)
		setupOptionsMenu()
		binding.apply {setupViews(readTextCoordinator, readTextView, readTextAppbar, readTextHolder)}

		searchQueryET = findViewById(org.fossify.commons.R.id.search_query)
		searchPrevBtn = findViewById(org.fossify.commons.R.id.search_previous)
		searchNextBtn = findViewById(org.fossify.commons.R.id.search_next)
		searchClearBtn = findViewById(org.fossify.commons.R.id.search_clear)
		if(checkAppSideloading()) return

		uri = intent.data
		val sp = intent.getStringExtra(REAL_FILE_PATH)
		path = sp?:uri?.let {getRealPathFromURI(it)}
		if(uri == null && path == null) {
			toast(org.fossify.commons.R.string.unknown_error_occurred)
			finish()
			return
		}

		val filename = (path?:uri?.path?:"").getFilenameFromPath()
		if(filename.isNotEmpty()) binding.readTextToolbar.title = filename

		binding.readTextView.onGlobalLayout {checkIntent(savedInstanceState)}
		setupSearchButtons()
	}

	override fun onResume() {
		super.onResume()
		setupTopAppBar(binding.readTextAppbar, NavigationIcon.Arrow)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		if(originalText != binding.readTextView.text.toString()) {
			outState.putString(KEY_UNSAVED_TEXT, binding.readTextView.text.toString())
		}
	}

	override fun onBackPressedCompat(): Boolean {
		val hasUnsavedChanges = originalText != binding.readTextView.text.toString()
		return when {
			isSearchActive -> {
				closeSearch()
				true
			} hasUnsavedChanges && System.currentTimeMillis() - lastSavePromptTS > SAVE_DISCARD_PROMPT_INTERVAL -> {
				lastSavePromptTS = System.currentTimeMillis()
				ConfirmationAdvancedDialog(this, "", org.fossify.commons.R.string.save_before_closing,
						org.fossify.commons.R.string.save, org.fossify.commons.R.string.discard) {
					if(it) saveText(true) else performDefaultBack()
				}
				true
			} else -> false
		}
	}

	private fun setupOptionsMenu() {
		binding.readTextToolbar.setOnMenuItemClickListener {menuItem ->
			when(menuItem.itemId) {
				R.id.menu_search -> openSearch()
				R.id.menu_save -> saveText()
				R.id.menu_save_as -> saveAsText()
				R.id.menu_open_with -> launchPath(intent.dataString!!, true)
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

	private fun saveAsText(exitAfterSaving: Boolean = false) {
		SaveAsDialog(this, path?:"", false) {path, _ ->
			val overwriteText = path == this.path
			if(hasStoragePermission()) writeFile(path, exitAfterSaving, overwriteText)
			else toast(org.fossify.commons.R.string.no_storage_permissions)
		}
	}

	private fun saveText(exitAfterSaving: Boolean = false) {
		if(path == null) saveAsText(exitAfterSaving)
		else if(hasStoragePermission()) writeFile(path!!, exitAfterSaving, true)
		else toast(org.fossify.commons.R.string.no_storage_permissions)
	}

	private fun writeFile(path: String, exitAfterSaving: Boolean, overwriteText: Boolean) = ensureBackgroundThread {
		try {
			val os = ListItem.getOutputStream(this, path)
			val text = binding.readTextView.text.toString()
			os.bufferedWriter().use {it.write(text)}
			toast(org.fossify.commons.R.string.file_saved)
			hideKeyboard()
			if(overwriteText) originalText = text
			if(exitAfterSaving) performDefaultBack()
		} catch(e: Throwable) {error(e)}
	}

	private fun printText() {
		try {
			val webView = WebView(this)
			webView.webViewClient = object: WebViewClient() {
				override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
				override fun onPageFinished(view: WebView, url: String) {createWebPrintJob(view)}
			}
			val text = binding.readTextView.text.toString()
			ensureBackgroundThread {
				try {
					val base64 = Base64.encodeToString(text.toByteArray(), Base64.DEFAULT)
					runOnUiThread {webView.loadData(base64, "text/plain", "base64")}
				} catch(e: Throwable) {error(e)}
			}
		} catch(e: Throwable) {error(e)}
	}

	private fun createWebPrintJob(webView: WebView) {
		val jobName = binding.readTextToolbar.title.toString()
		val printAdapter = webView.createPrintDocumentAdapter(jobName)
		(getSystemService(PRINT_SERVICE) as? PrintManager)?.apply {
			print(jobName, printAdapter, PrintAttributes.Builder().build())
		}
	}

	private fun openInputStream() = if(path != null) ListItem.getInputStream(this, path!!)
		else contentResolver.openInputStream(uri!!)

	private fun checkIntent(state: Bundle?) = ensureBackgroundThread {
		try {
			originalText = openInputStream()!!.bufferedReader().use {it.readText()}
		} catch(e: Throwable) {
			error(e)
			finish()
			return@ensureBackgroundThread
		}
		runOnUiThread {
			val text = if(state == null) originalText
				else state.getString(KEY_UNSAVED_TEXT, originalText)
			binding.readTextView.setText(text)
			if(originalText.isNotEmpty()) hideKeyboard()
			else showKeyboard(binding.readTextView)
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