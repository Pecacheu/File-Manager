package org.fossify.filemanager.helpers

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.hierynomus.msfscc.FileAttributes.*
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.fossify.filemanager.extensions.*
import org.fossify.filemanager.models.ListItem
import org.json.JSONObject
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.security.Key
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

const val TIMEOUT = 120L
const val SO_TIMEOUT = 240L
const val URI_BASE = REMOTE_URI.length + UUID.LENGTH + 1

class Remote(data: JSONObject) {
	val id: UUID = UUID.from(data.getString("i"))
	var name: String = data.getString("n")
	var type: Int = data.getInt("t")
	var host: String = data.getString("h")
	var usr: String = data.getString("u")
	internal var pwdKey: String = data.optString("p") //TODO TEMP, will be private later

	//SMB Only
	var share: String
	var domain: String
	private var mount: DiskShare? = null

	constructor(data: String): this(JSONObject(data))
	init {
		if(type == 1) { //SMB
			share = data.getString("s")
			domain = data.optString("d")
		} else {
			throw IllegalArgumentException("Unknown type $type")
		}
	}

	class KeyException(cause: Throwable): Exception(cause)
	companion object {
		fun newSMB(name: String, host: String, usr: String, share: String): Remote {
			val obj = JSONObject()
			obj.put("i", UUID.genUUID().toString())
			obj.put("n", name)
			obj.put("t", 1)
			obj.put("h", host)
			obj.put("u", usr)
			obj.put("s", share)
			return Remote(obj)
		}
		fun clearKeys() {
			KeyStore.getInstance(KEYSTORE).apply {load(null); deleteEntry(KEY_NAME)}
		}
	}

	val basePath get() = "$REMOTE_URI$id:"
	override fun toString(): String {
		val obj = JSONObject()
		obj.put("i", id)
		obj.put("n", name)
		obj.put("t", type)
		obj.put("h", host)
		obj.put("u", usr)
		if(pwdKey.isNotEmpty()) obj.put("p", pwdKey)
		obj.put("s", share)
		if(domain.isNotEmpty()) obj.put("d", domain)
		return obj.toString()
	}

	private fun getKey(newIfNone: Boolean): Key {
		val ks = KeyStore.getInstance(KEYSTORE)
		ks.load(null)
		var key = ks.getEntry(KEY_NAME, null) as? KeyStore.SecretKeyEntry
		if(key == null) {
			if(!newIfNone) throw FileNotFoundException("No encryption key found")
			val kg = KeyGenerator.getInstance(KEY_TYPE, ks.provider)
			kg.init(KeyGenParameterSpec.Builder(KEY_NAME,
				KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
				.setEncryptionPaddings(KEY_PAD)
				.setBlockModes(KEY_BLK)
				.setRandomizedEncryptionRequired(false)
				.build())
			return kg.generateKey()
		}
		return key.secretKey
	}

	fun setPwd(pwd: String) {
		val iv = ByteArray(KEY_IV)
		SecureRandom.getInstanceStrong().nextBytes(iv)
		val c = Cipher.getInstance(CIPHER)
		try {c.init(Cipher.ENCRYPT_MODE, getKey(true), GCMParameterSpec(KEY_TAG, iv))}
		catch(e: Throwable) {throw KeyException(e)}
		val pKey = c.doFinal(pwd.toByteArray())
		val buf = ByteBuffer.allocate(KEY_IV + pKey.size)
		buf.put(iv)
		buf.put(pKey)
		pwdKey = buf.array().toBase64()
	}

	fun getPwd(): String {
		try {
			if(pwdKey.isEmpty()) return ""
			val pk = pwdKey.fromBase64()
			if(pk.size <= KEY_IV) throw IllegalArgumentException("Bad key format")
			val iv = pk.sliceArray(IntRange(0, KEY_IV-1))
			val pKey = pk.sliceArray(IntRange(KEY_IV, pk.size-1))
			val c = Cipher.getInstance(CIPHER)
			c.init(Cipher.DECRYPT_MODE, getKey(false), GCMParameterSpec(KEY_TAG, iv))
			return c.doFinal(pKey).decodeToString()
		} catch(e: Throwable) {
			throw Error("$e. Try resetting the password", e)
		}
	}

	private val mntValid
		get() = mount != null && mount!!.isConnected

	fun connect() {
		if(type == 1) { //SMB
			if(mntValid) return
			val hs = host.split(':')
			var port = 445
			try {port = hs[1].toInt()}
			catch(_: NumberFormatException) {}
			catch(_: IndexOutOfBoundsException) {}
			val pwd = getPwd()
			Log.i("test", "Connecting to $name @ ${hs[0]}:$port $domain '$usr','$pwd'")

			val con = SMBClient(SmbConfig.builder()
				.withTimeout(TIMEOUT, TimeUnit.SECONDS)
				.withSoTimeout(SO_TIMEOUT, TimeUnit.SECONDS)
				.build()).connect(hs[0], port)
			val ac = AuthenticationContext(usr, pwd.toCharArray(), domain)
			mount = con.authenticate(ac).connectShare(share) as DiskShare
		}
	}

	fun listDir(path: String, hidden: Boolean): ArrayList<ListItem> {
		val files = ArrayList<ListItem>()
		val p = "${path.trimEnd('/')}/"
		if(mntValid) {
			val psTmp = p.substring(URI_BASE) //TODO TEMP
			val ls = mount!!.list(psTmp)
			Log.i("test", "SharePath '$psTmp' got ${ls.size} items")
			for(f in ls) {
				if(f.fileName == "." || f.fileName == "..") continue
				val attr = f.fileAttributes
				if(!hidden && attr and FILE_ATTRIBUTE_HIDDEN.value != 0L) continue
				val isDir = attr and FILE_ATTRIBUTE_DIRECTORY.value != 0L
				files.add(ListItem("$p${f.fileName}", f.fileName, isDir, 0,
					f.endOfFile, f.lastWriteTime.toEpochMillis(), false, false))
			}
		}
		return files
	}

	fun getChildCount(path: String, hidden: Boolean): Int {
		val p = "${path.substring(URI_BASE).trimEnd('/')}/"
		var cnt = 0
		if(mntValid) for(f in mount!!.list(p)) {
			if(f.fileName == "." || f.fileName == "..") continue
			val attr = f.fileAttributes
			if(hidden || attr and FILE_ATTRIBUTE_HIDDEN.value == 0L) ++cnt
		}
		return cnt
	}
}