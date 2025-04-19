package org.fossify.filemanager

import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.FossifyApp
import org.fossify.filemanager.helpers.Config

class App: FossifyApp() {
	internal lateinit var conf: Config

	override fun onCreate() {
		super.onCreate()
		Reprint.initialize(this)
		conf = Config(this)
	}
}