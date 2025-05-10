package org.fossify.filemanager.interfaces

interface ItemOperationsListener {
	fun refreshFragment()
	fun selectedPaths(paths: ArrayList<String>)
	fun setupDateTimeFormat()
	fun setupFontSize()
	fun toggleFilenameVisibility()
	fun columnCountChanged()
	fun finishActMode()
}