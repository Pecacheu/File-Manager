package org.fossify.filemanager.helpers

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.signature.ObjectKey
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes.*
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.filemanager.R
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

class Remote(val ctx: Context, data: JSONObject) {
	class KeyException(cause: Throwable): Exception(cause)
	companion object {
		const val TIMEOUT = 120L
		const val SO_TIMEOUT = 240L
		const val URI_BASE = REMOTE_URI.length + UUID.LENGTH + 1

		//Types
		val TYPES = arrayOf("Type", "SMB")
		const val SMB = 1

		fun SMBData(ctx: Context, id: UUID?, name: String, host: String, usr: String, share: String, domain: String): JSONObject {
			if(name.isBlank()) throw ctx.formatErr(R.string.not_opt, null, ctx.getString(org.fossify.commons.R.string.name))
			if(host.isBlank()) throw ctx.formatErr(R.string.not_opt, null, ctx.getString(R.string.host))
			if(share.isBlank()) ctx.formatErr(R.string.not_opt, null, ctx.getString(org.fossify.commons.R.string.share))
			val obj = JSONObject()
			obj.put("i", id?:UUID.genUUID().toString())
			obj.put("n", name)
			obj.put("t", SMB)
			obj.put("h", host)
			obj.put("u", usr)
			obj.put("s", share)
			obj.put("d", domain)
			return obj
		}
		fun clearKeys(ctx: Context) {
			KeyStore.getInstance(KEYSTORE).apply {load(null); deleteEntry(KEY_NAME)}
			for(r in ctx.config.getRemotes()) r.value._pwdKey = ""
			ctx.config.setRemotes()
		}
	}

	private lateinit var _name: String
	private var _type: Int = 0
	private lateinit var _host: String
	private lateinit var _usr: String
	private var _pwdKey: String = ""

	val id = UUID.from(data.getString("i"))
	val name get() = _name
	val type get() = _type
	val host get() = _host
	val usr get() = _usr
	internal val pwdKey get() = _pwdKey

	//SMB Only
	private lateinit var _share: String
	private lateinit var _domain: String
	private var mount: DiskShare? = null
	private var smb: Connection? = null

	val share get() = _share
	val domain get() = _domain

	constructor(ctx: Context, data: String): this(ctx, JSONObject(data))
	init {init(data)}

	fun init(data: JSONObject): Remote {
		_name = data.getString("n")
		_type = data.getInt("t")
		_host = data.getString("h")
		_usr = data.optString("u")
		if(pwdKey.isEmpty()) _pwdKey = data.optString("p")
		if(type == SMB) {
			_share = data.getString("s")
			_domain = data.optString("d")
		} else throw ctx.formatErr(R.string.unknown_type, null, type)
		return this
	}

	val basePath get() = "$REMOTE_URI$id:"
	override fun toString(): String {
		val obj = JSONObject()
		obj.put("i", id)
		obj.put("n", name)
		obj.put("t", type)
		obj.put("h", host)
		if(usr.isNotBlank()) obj.put("u", usr)
		if(pwdKey.isNotBlank()) obj.put("p", pwdKey)
		obj.put("s", share)
		if(domain.isNotBlank()) obj.put("d", domain)
		return obj.toString()
	}

	private fun getKey(newIfNone: Boolean): Key {
		val ks = KeyStore.getInstance(KEYSTORE)
		ks.load(null)
		var key = ks.getEntry(KEY_NAME, null) as? KeyStore.SecretKeyEntry
		if(key == null) {
			if(!newIfNone) throw FileNotFoundException(ctx.getString(R.string.no_key_err))
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
		if(pwd.isBlank()) {
			_pwdKey = ""
			return
		}
		val iv = ByteArray(KEY_IV)
		SecureRandom.getInstanceStrong().nextBytes(iv)
		val c = Cipher.getInstance(CIPHER)
		try {c.init(Cipher.ENCRYPT_MODE, getKey(true), GCMParameterSpec(KEY_TAG, iv))}
		catch(e: Throwable) {throw KeyException(e)}
		val pKey = c.doFinal(pwd.toByteArray())
		val buf = ByteBuffer.allocate(KEY_IV + pKey.size)
		buf.put(iv)
		buf.put(pKey)
		_pwdKey = buf.array().toBase64()
	}

	private fun getPwd(): String {
		try {
			if(pwdKey.isEmpty()) return ""
			val pk = pwdKey.fromBase64()
			if(pk.size <= KEY_IV) throw IllegalArgumentException(ctx.getString(R.string.key_fmt_err))
			val iv = pk.sliceArray(IntRange(0, KEY_IV-1))
			val pKey = pk.sliceArray(IntRange(KEY_IV, pk.size-1))
			val c = Cipher.getInstance(CIPHER)
			c.init(Cipher.DECRYPT_MODE, getKey(false), GCMParameterSpec(KEY_TAG, iv))
			return c.doFinal(pKey).decodeToString()
		} catch(e: Throwable) {
			throw ctx.formatErr(R.string.key_err_hint, e, e.toString())
		}
	}

	private val mntValid
		get() = mount != null && mount!!.isConnected

	fun close() {
		mount?.close(); mount = null
		smb?.close(); smb = null
	}

	fun connect() {
		if(type == SMB) {
			if(mntValid) return
			val hs = host.split(':')
			var port = 445
			try {port = hs[1].toInt()}
			catch(_: NumberFormatException) {}
			catch(_: IndexOutOfBoundsException) {}
			val pwd = getPwd()
			Log.i("test", "Connecting to $name @ ${hs[0]}:$port $domain,$usr")

			smb = SMBClient(SmbConfig.builder()
				.withTimeout(TIMEOUT, TimeUnit.SECONDS)
				.withSoTimeout(SO_TIMEOUT, TimeUnit.SECONDS)
				.build()).connect(hs[0], port)
			val ac = if(usr.isBlank() || pwd.isBlank()) AuthenticationContext.guest()
				else AuthenticationContext(usr, pwd.toCharArray(), domain)
			mount = smb!!.authenticate(ac).connectShare(share) as DiskShare
		}
	}

	fun listDir(path: String, act: BaseSimpleActivity): ArrayList<ListItem> {
		if(!mntValid) connect()
		val hidden = act.config.shouldShowHidden()
		val files = ArrayList<ListItem>()
		val p = path.trimEnd('/')
		val ls = mount!!.list(p.substring(URI_BASE)) //TODO Support searchPattern for better performance?
		for(f in ls) {
			if(f.fileName == "." || f.fileName == "..") continue
			val attr = f.fileAttributes
			if(!hidden && attr and FILE_ATTRIBUTE_HIDDEN.value != 0L) continue
			val isDir = attr and FILE_ATTRIBUTE_DIRECTORY.value != 0L
			files.add(ListItem(act, "$p/${f.fileName}", f.fileName, isDir,
				-1, f.endOfFile, f.lastWriteTime.toEpochMillis()))
		}
		return files
	}

	fun getChildCount(path: String, hidden: Boolean): Int {
		val p = path.substring(URI_BASE)
		var cnt = 0
		if(mntValid) for(f in mount!!.list(p)) {
			if(f.fileName == "." || f.fileName == "..") continue
			val attr = f.fileAttributes
			if(hidden || attr and FILE_ATTRIBUTE_HIDDEN.value == 0L) ++cnt
		}
		return cnt
	}

	fun mkDir(path: String): Boolean {
		if(!mntValid) connect()
		val p = path.substring(URI_BASE)
		try {
			mount!!.openDirectory(p, setOf(AccessMask.FILE_LIST_DIRECTORY, AccessMask.FILE_ADD_SUBDIRECTORY),
				setOf(FILE_ATTRIBUTE_DIRECTORY), SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE,
				setOf(SMB2CreateOptions.FILE_DIRECTORY_FILE)).close()
			return true
		} catch(e: SMBApiException) {
			when(e.status) {
				NtStatus.STATUS_OBJECT_NAME_COLLISION -> return false
				else -> throw e
			}
		}
	}

	fun exists(path: String, dirMode: Int): Boolean {
		if(!mntValid) connect()
		val p = path.substring(URI_BASE)
		val cOpt = mutableSetOf<SMB2CreateOptions>()
		if(dirMode == 1) cOpt.add(SMB2CreateOptions.FILE_DIRECTORY_FILE)
		else if(dirMode == 2) cOpt.add(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
		try {
			mount!!.open(p, setOf(AccessMask.FILE_READ_ATTRIBUTES), setOf(FILE_ATTRIBUTE_NORMAL),
				SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, cOpt).close()
			return true
		} catch(e: SMBApiException) {
			when(e.status) {
				NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND,
				NtStatus.STATUS_FILE_IS_A_DIRECTORY, NtStatus.STATUS_NOT_A_DIRECTORY,
				NtStatus.STATUS_DELETE_PENDING -> return false
				else -> throw e
			}
		}
	}

	fun delete(path: String) {
		if(!mntValid) connect()
		val p = path.substring(URI_BASE)
		try {
			mount!!.open(p, setOf(AccessMask.DELETE), setOf(FILE_ATTRIBUTE_NORMAL),
				SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use {it.deleteOnClose()}
		} catch(e: SMBApiException) {
			when(e.status) {
				NtStatus.STATUS_DIRECTORY_NOT_EMPTY -> mount!!.rmdir(p, true)
				NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND,
				NtStatus.STATUS_DELETE_PENDING -> return
				else -> throw e
			}
		}
	}

	fun openFile(path: String, write: Boolean): File {
		if(!mntValid) connect()
		val p = path.substring(URI_BASE)
		val mask = mutableSetOf(AccessMask.FILE_READ_DATA)
		if(write) mask.add(AccessMask.FILE_WRITE_DATA)
		val acc = mutableSetOf(SMB2ShareAccess.FILE_SHARE_READ)
		if(write) acc.add(SMB2ShareAccess.FILE_SHARE_WRITE)
		val mode = SMB2CreateDisposition.FILE_OPEN
		val cOpt = setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_RANDOM_ACCESS)
		val file = mount!!.openFile(p, mask, null, acc, mode, cOpt)
		return file
	}

	fun openFileProxy(path: String, write: Boolean): ParcelFileDescriptor {
		val file = openFile(path, write)
		val len = file.fileInformation.standardInformation.endOfFile
		Log.i("test", "Open file $path with size $len")
		val sMan = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
		val mode = if(write) ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_READ_ONLY
		val ht = HandlerThread("Transfer")
		ht.start()
		return sMan.openProxyFileDescriptor(mode, object: ProxyFileDescriptorCallback() {
			override fun onGetSize() = len
			override fun onRead(ofs: Long, size: Int, data: ByteArray) = file.read(data, ofs, 0, size)
			override fun onWrite(ofs: Long, size: Int, data: ByteArray) = file.write(data, ofs, 0, size).toInt()
			override fun onFsync() = file.flush()
			override fun onRelease() = file.close()
		}, Handler(ht.looper))
	}
}

// ---- Glide Support ----

@GlideModule
class RemoteModule: AppGlideModule() {
	override fun registerComponents(ctx: Context, g: Glide, reg: Registry) {
		reg.prepend(String::class.java, ParcelFileDescriptor::class.java, RemoteFactory(ctx))
	}
}

private class RemoteFactory(val ctx: Context): ModelLoaderFactory<String, ParcelFileDescriptor> {
	override fun build(mf: MultiModelLoaderFactory) = RemoteLoader(ctx)
	override fun teardown() {}
}

private class RemoteLoader(val ctx: Context): ModelLoader<String, ParcelFileDescriptor> {
	override fun handles(path: String) = isRemotePath(path)
	override fun buildLoadData(path: String, w: Int, h: Int, opts: Options) =
		ModelLoader.LoadData(ObjectKey(path), RemoteFetcher(path, ctx))
}

private class RemoteFetcher(val path: String, val ctx: Context): DataFetcher<ParcelFileDescriptor> {
	private var proxy: ParcelFileDescriptor? = null

	override fun loadData(pri: Priority, cb: DataFetcher.DataCallback<in ParcelFileDescriptor>) {
		proxy = ctx.config.getRemoteForPath(path)?.openFileProxy(path, false)
		cb.onDataReady(proxy)
	}
	override fun cancel() {}
	override fun cleanup() {proxy?.close()}
	override fun getDataClass() = ParcelFileDescriptor::class.java
	override fun getDataSource() = DataSource.REMOTE
}