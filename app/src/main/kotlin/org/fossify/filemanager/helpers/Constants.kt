package org.fossify.filemanager.helpers

import android.security.keystore.KeyProperties
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_FILES
import org.fossify.commons.helpers.TAB_RECENT_FILES
import org.fossify.commons.helpers.TAB_STORAGE_ANALYSIS
import org.fossify.commons.helpers.isOnMainThread

const val MAX_COLUMN_COUNT = 15

//Keys
const val KEYSTORE = "AndroidKeyStore"
const val KEY_NAME = "remote_key"
const val KEY_TYPE = KeyProperties.KEY_ALGORITHM_AES
const val KEY_BLK = KeyProperties.BLOCK_MODE_GCM
const val KEY_PAD = KeyProperties.ENCRYPTION_PADDING_NONE
const val CIPHER = "$KEY_TYPE/$KEY_BLK/$KEY_PAD"
const val KEY_IV = 12
const val KEY_TAG = 12 * Byte.SIZE_BITS

//Shared Prefs
const val SHOW_HIDDEN = "show_hidden"
const val PRESS_BACK_TWICE = "press_back_twice"
const val HOME_FOLDER = "home_folder"
const val TEMPORARILY_SHOW_HIDDEN = "temporarily_show_hidden"
const val IS_ROOT_AVAILABLE = "is_root_available"
const val ENABLE_ROOT_ACCESS = "enable_root_access"
const val EDITOR_TEXT_ZOOM = "editor_text_zoom"
const val VIEW_TYPE_PREFIX = "view_type_folder_"
const val FILE_COLUMN_CNT = "file_column_cnt"
const val FILE_LANDSCAPE_COLUMN_CNT = "file_landscape_column_cnt"
const val DISPLAY_FILE_NAMES = "display_file_names"
const val SHOW_TABS = "show_tabs"
const val LAST_PATH = "last_path"
const val REMOTES = "remotes"

//Open As
const val OPEN_AS_DEFAULT = 0
const val OPEN_AS_TEXT = 1
const val OPEN_AS_IMAGE = 2
const val OPEN_AS_AUDIO = 3
const val OPEN_AS_VIDEO = 4
const val OPEN_AS_OTHER = 5

const val ALL_TABS_MASK = TAB_FILES or TAB_FAVORITES or TAB_RECENT_FILES or TAB_STORAGE_ANALYSIS
const val IMAGES = "images"
const val VIDEOS = "videos"
const val AUDIO = "audio"
const val DOCUMENTS = "documents"
const val ARCHIVES = "archives"
const val OTHERS = "others"
const val SHOW_MIMETYPE = "show_mimetype"

const val VOLUME_NAME = "volume_name"
const val PRIMARY_VOLUME_NAME = "external_primary"
const val REMOTE_URI = "r@"

//What else should we count as an audio except "audio/*" mimetype
val extraAudioMimeTypes = arrayListOf("application/ogg")
val extraDocumentMimeTypes = arrayListOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
	"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/javascript")

val archiveMimeTypes = arrayListOf("application/zip", "application/octet-stream", "application/json", "application/x-tar", "application/x-rar-compressed",
	"application/x-zip-compressed", "application/x-7z-compressed", "application/x-compressed", "application/x-gzip", "application/java-archive",
	"multipart/x-zip")