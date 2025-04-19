package org.fossify.filemanager.helpers

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import org.fossify.commons.extensions.getInternalStoragePath
import org.fossify.commons.helpers.BaseConfig
import java.io.File
import java.util.Locale
import androidx.core.content.edit
import org.fossify.filemanager.extensions.UUID
import org.fossify.filemanager.extensions.idFromRemotePath

class Config(context: Context): BaseConfig(context) {
	init {Log.i("test", "New config...")} //TODO TEMP

	var showHidden: Boolean
		get() = prefs.getBoolean(SHOW_HIDDEN, false)
		set(show) = prefs.edit {putBoolean(SHOW_HIDDEN, show)}

	var temporarilyShowHidden: Boolean
		get() = prefs.getBoolean(TEMPORARILY_SHOW_HIDDEN, false)
		set(temporarilyShowHidden) = prefs.edit {putBoolean(TEMPORARILY_SHOW_HIDDEN, temporarilyShowHidden)}

	fun shouldShowHidden() = showHidden || temporarilyShowHidden

	var pressBackTwice: Boolean
		get() = prefs.getBoolean(PRESS_BACK_TWICE, true)
		set(pressBackTwice) = prefs.edit {putBoolean(PRESS_BACK_TWICE, pressBackTwice)}

	var homeFolder: String
		get(): String {
			var path = prefs.getString(HOME_FOLDER, "")!!
			if(path.isEmpty() || !File(path).isDirectory) {
				path = context.getInternalStoragePath()
				homeFolder = path
			}
			return path
		}
		set(homeFolder) = prefs.edit {putString(HOME_FOLDER, homeFolder)}

	fun addFavorite(path: String) {
		synchronized(this) {
			val favs = HashSet<String>(favorites)
			favs.add(path)
			favorites = favs
		}
	}
	fun moveFavorite(oldPath: String, newPath: String) {
		synchronized(this) {
			if(!favorites.contains(oldPath)) return
			val favs = HashSet<String>(favorites)
			favs.remove(oldPath)
			favs.add(newPath)
			favorites = favs
		}
	}
	fun removeFavorite(path: String) {
		synchronized(this) {
			if(!favorites.contains(path)) return
			val favs = HashSet<String>(favorites)
			favs.remove(path)
			favorites = favs
		}
	}

	var isRootAvailable: Boolean
		get() = prefs.getBoolean(IS_ROOT_AVAILABLE, false)
		set(isRootAvailable) = prefs.edit {putBoolean(IS_ROOT_AVAILABLE, isRootAvailable)}

	var enableRootAccess: Boolean
		get() = prefs.getBoolean(ENABLE_ROOT_ACCESS, false)
		set(enableRootAccess) = prefs.edit {putBoolean(ENABLE_ROOT_ACCESS, enableRootAccess)}

	var editorTextZoom: Float
		get() = prefs.getFloat(EDITOR_TEXT_ZOOM, 1.2f)
		set(editorTextZoom) = prefs.edit {putFloat(EDITOR_TEXT_ZOOM, editorTextZoom)}

	fun saveFolderViewType(path: String, value: Int) {
		if(path.isEmpty()) viewType = value
		else prefs.edit {putInt(VIEW_TYPE_PREFIX + path.lowercase(Locale.getDefault()), value)}
	}

	fun getFolderViewType(path: String) = prefs.getInt(VIEW_TYPE_PREFIX + path.lowercase(Locale.getDefault()), viewType)

	fun removeFolderViewType(path: String) {
		prefs.edit {remove(VIEW_TYPE_PREFIX + path.lowercase(Locale.getDefault()))}
	}

	fun hasCustomViewType(path: String) = prefs.contains(VIEW_TYPE_PREFIX + path.lowercase(Locale.getDefault()))

	var fileColumnCnt: Int
		get() = prefs.getInt(getFileColumnsField(), getDefaultFileColumnCount())
		set(fileColumnCnt) = prefs.edit {putInt(getFileColumnsField(), fileColumnCnt)}

	private fun getFileColumnsField(): String {
		val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
		return if(isPortrait) FILE_COLUMN_CNT else FILE_LANDSCAPE_COLUMN_CNT
	}

	private fun getDefaultFileColumnCount(): Int {
		val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
		return if(isPortrait) 4 else 8
	}

	var displayFilenames: Boolean
		get() = prefs.getBoolean(DISPLAY_FILE_NAMES, true)
		set(displayFilenames) = prefs.edit {putBoolean(DISPLAY_FILE_NAMES, displayFilenames)}

	var showTabs: Int
		get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
		set(showTabs) = prefs.edit {putInt(SHOW_TABS, showTabs)}

	var lastPath: String
		get() = prefs.getString(LAST_PATH, "")!!
		set(lastPath) = prefs.edit {putString(LAST_PATH, lastPath)}

	private var remotes: HashMap<String, Remote>? = null

	fun addRemote(remote: Remote) {
		if(remotes == null) getRemotes()
		for(r in remotes!!)
			if(r.value.id == remote.id || r.value.name == remote.name)
				throw ArrayStoreException("Remote already exists")
		remotes!!.put(remote.id.toString(), remote)
		setRemotes()
	}
	fun removeRemote(id: UUID) {
		if(remotes == null) getRemotes()
		remotes!!.remove(id.toString())
		setRemotes()
	}
	fun setRemotes() {
		synchronized(this) {
			val rSet = HashSet<String>(remotes!!.size)
			for(r in remotes!!) rSet.add(r.value.toString())
			Log.i("test", "Set remotes to $rSet")
			prefs.edit {remove(REMOTES).putStringSet(REMOTES, rSet)}
		}
	}

	fun getRemotes(removeBad: Boolean=false): HashMap<String, Remote> {
		synchronized(this) {
			if(remotes == null) {
				Log.i("test", "Init remotes...")
				val rSet = prefs.getStringSet(REMOTES, HashSet())!! as HashSet<String>
				var changed = false //Haha like the game
				remotes = HashMap<String, Remote>(rSet.size)
				var r: Remote?
				for(rs in rSet) {
					r = null
					try {
						r = Remote(rs)
					} catch(e: Throwable) {
						Log.e("files", "Error loading remote $rs", e)
						if(!removeBad) throw e
						changed = true
					}
					if(r != null) remotes!!.put(r.id.toString(), r)
				}
				if(changed) setRemotes()
			}
			return remotes!!
		}
	}
	fun getRemoteForPath(path: String): Remote? {
		if(remotes == null) getRemotes()
		return remotes!![context.idFromRemotePath(path)]
	}
}