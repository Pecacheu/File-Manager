package org.fossify.filemanager.interfaces

interface ItemOperationsListener {
	fun refreshFragment()
	fun setupDateTimeFormat()
	fun setupFontSize()
	fun toggleFilenameVisibility()
	fun finishActMode()
}