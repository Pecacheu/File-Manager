package org.fossify.filemanager.helpers

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import org.fossify.commons.helpers.BaseConfig
import java.util.Locale
import androidx.core.content.edit
import org.fossify.commons.extensions.toast
import org.fossify.filemanager.R
import org.fossify.filemanager.extensions.UUID
import org.fossify.filemanager.extensions.formatErr
import org.fossify.filemanager.extensions.idFromRemotePath
import org.fossify.filemanager.extensions.isRemotePath

class Config(context: Context): BaseConfig(context) {
	var showHidden: Boolean
		get() = prefs.getBoolean(SHOW_HIDDEN, false)
		set(show) = prefs.edit {putBoolean(SHOW_HIDDEN, show)}

	var temporarilyShowHidden: Boolean
		get() = prefs.getBoolean(TEMPORARILY_SHOW_HIDDEN, false)
		set(temporarilyShowHidden) = prefs.edit {putBoolean(TEMPORARILY_SHOW_HIDDEN, temporarilyShowHidden)}

	fun shouldShowHidden() = showHidden || temporarilyShowHidden

	var reloadPath = false

	var pressBackTwice: Boolean
		get() = prefs.getBoolean(PRESS_BACK_TWICE, true)
		set(pressBackTwice) = prefs.edit {putBoolean(PRESS_BACK_TWICE, pressBackTwice)}

	fun getHome(path: String): String {
		if(isRemotePath(path)) {
			val r = getRemoteForPath(path)?:return getHome("")
			return ("${r.basePath}/${r.home}").trimEnd('/')
		}
		val home = prefs.getString(HOME_FOLDER, "")!!
		return ("$internalStoragePath/$home").trimEnd('/')
	}
	fun setHome(home: String) {
		var hp = home.trimEnd('/')
		if(isRemotePath(hp)) {
			val r = getRemoteForPath(hp)?:return
			r.home = hp.substring(Remote.URI_BASE).trimStart('/')
			setRemotes()
		} else if(hp.startsWith(internalStoragePath)) {
			hp = hp.substring(internalStoragePath.length).trimStart('/')
			prefs.edit {putString(HOME_FOLDER, hp)}
		} else {
			context.toast(context.getString(R.string.bad_home))
		}
	}

	fun addFavorite(path: String) {
		synchronized(this) {
			val favs = HashSet<String>(favorites)
			favs.add(path)
			favorites = favs
		}
	}
	fun moveFavorite(oldPath: String, newPath: String) {
		//TODO Detect fav inside of moved folder recursively
		synchronized(this) {
			if(!favorites.contains(oldPath)) return
			val favs = HashSet<String>(favorites)
			favs.remove(oldPath)
			favs.add(newPath)
			favorites = favs
		}
	}
	fun removeFavorite(path: String) {
		//TODO Detect fav inside of deleted folder recursively
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
		val ns = remote.name.lowercase()
		for(r in remotes!!)
			if(r.value.id == remote.id || r.value.name.lowercase() == ns)
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
			prefs.edit {remove(REMOTES).putStringSet(REMOTES, rSet)}
		}
	}

	fun getRemotes(removeBad: Boolean=false): HashMap<String, Remote> {
		synchronized(this) {
			if(removeBad || remotes == null) {
				val rSet = prefs.getStringSet(REMOTES, HashSet())!! as HashSet<String>
				var changed = false //Haha like the game
				remotes = HashMap<String, Remote>(rSet.size)
				var r: Remote?
				for(rs in rSet) {
					r = null
					try {
						r = Remote(context, rs)
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
	fun getRemoteForPath(path: String, force: Boolean=false): Remote? {
		if(remotes == null) getRemotes()
		val r = remotes!![idFromRemotePath(path)]
		if(r == null && force) throw context.formatErr(R.string.no_remote_err)
		return r
	}
}