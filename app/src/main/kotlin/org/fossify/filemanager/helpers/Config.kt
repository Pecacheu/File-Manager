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
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.PrivateKey
import java.util.Base64
import javax.crypto.Cipher

class Config(context: Context): BaseConfig(context) {
	companion object {
		fun newInstance(context: Context) = Config(context)
	}

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
		val favs = HashSet<String>(favorites)
		favs.add(path)
		favorites = favs
	}
	fun moveFavorite(oldPath: String, newPath: String) {
		if(!favorites.contains(oldPath)) return
		val favs = HashSet<String>(favorites)
		favs.remove(oldPath)
		favs.add(newPath)
		favorites = favs
	}
	fun removeFavorite(path: String) {
		if(!favorites.contains(path)) return
		val favs = HashSet<String>(favorites)
		favs.remove(path)
		favorites = favs
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

	private var remotes: HashSet<String>
		get() = prefs.getStringSet(REMOTES, HashSet())!! as HashSet<String>
		set(remotes) = prefs.edit {remove(REMOTES).putStringSet(REMOTES, remotes)}

	fun addRemote(remote: Remote) {
		val rSet = remotes
		for(rs in rSet) {
			val r = Remote.from(rs)
			if(r.id == remote.id || r.name == remote.name)
				throw ArrayStoreException("Remote already exists")
		}
		rSet.add(remote.toString())
		remotes = rSet
	}
	fun removeRemote(id: String) {
		val rSet = remotes
		for(rs in remotes) if(rs.startsWith("$id|")) {
			rSet.remove(rs)
			break
		}
		remotes = rSet
	}
	fun getRemotes(removeBad: Boolean=false): ArrayList<Remote> {
		val rSet = remotes
		val rl = ArrayList<Remote>(rSet.size)
		var changed = false //Haha like the game
		for(rs in rSet) {
			try {
				rl.add(Remote.from(rs))
			} catch(e: IllegalArgumentException) {
				if(removeBad) {
					rSet.remove(rs)
					changed = true
				} else throw e
			}
		}
		if(changed) remotes = rSet
		return rl
	}
	fun getRemote(id: String): Remote? {
		for(rs in remotes) if(rs.startsWith("$id|")) return Remote.from(rs)
		return null
	}
}

class Remote(val id: UUID, var name: String, var host: String, var pwdKey: String) {
	companion object {
		fun from(data: String): Remote {
			val d = data.split('|')
			if(d.size != 4) throw IllegalArgumentException("Invalid Remote $data")
			return Remote(UUID.from(d[0]), d[1], d[2], d[3])
		}
	}

	override fun toString() = "$id|$name|$host|$pwdKey"
	fun getPath() = "$REMOTE_URI$id:"

	private fun getKey(newIfNone: Boolean): PrivateKey? {
		val ks = KeyStore.getInstance(KEYSTORE)
		val key = ks.getEntry(KEY_NAME, null) as? KeyStore.PrivateKeyEntry
		Log.i("test", "Got Key $key")
		//TODO New Key if none
		if(key == null && newIfNone) {
			/*val kpg = KeyPairGenerator.getInstance(KEY_TYPE, ks.provider)
			kpg.initialize(2048)
			val kp = kpg.genKeyPair()
			ks.setKeyEntry(KEY_NAME, kp.private, null, kp.public)*/
		}
		return key!!.privateKey
	}

	fun setPwd(pwd: String) {
		val c = Cipher.getInstance(CIPHER)
		c.init(Cipher.ENCRYPT_MODE, getKey(true))
		val pKey = c.doFinal(pwd.toByteArray())
		pwdKey = Base64.getUrlEncoder().withoutPadding().encodeToString(pKey)
	}

	fun getPwd(): String {
		val key = getKey(false)
		if(key == null) throw FileNotFoundException("No decryption key found")
		val c = Cipher.getInstance(CIPHER)
		c.init(Cipher.DECRYPT_MODE, key)
		val pKey = Base64.getUrlDecoder().decode(pwdKey)
		return c.doFinal(pKey).decodeToString()
	}
}