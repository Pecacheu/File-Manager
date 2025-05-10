package org.fossify.filemanager.helpers

import com.stericson.RootShell.execution.Command
import com.stericson.RootTools.RootTools
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.areDigitsOnly
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.filemanager.R
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.error
import org.fossify.filemanager.models.ListItem
import java.io.File

class RootHelpers(val activity: BaseSimpleActivity) {
	fun askRootIfNeeded(callback: (success: Boolean)->Unit) {
		val cmd = "ls -lA"
		val command = object: Command(0, cmd) {
			override fun commandOutput(id: Int, line: String) {
				callback(true)
				super.commandOutput(id, line)
			}
		}
		try {
			RootTools.getShell(true).add(command)
		} catch(exception: Exception) {
			activity.error(exception)
			callback(false)
		}
	}

	fun getFiles(path: String, callback: (originalPath: String, listItems: ArrayList<ListItem>)->Unit) {
		getFullLines(path) {
			val fullLines = it
			val files = ArrayList<ListItem>()
			val hiddenArgument = if(activity.config.shouldShowHidden()) "-A " else ""
			val cmd = "ls $hiddenArgument$path"

			val command = object: Command(0, cmd) {
				override fun commandOutput(id: Int, line: String) {
					val file = File(path, line)
					val fullLine = fullLines.firstOrNull {ln -> ln.endsWith(" $line")}
					val isDir = fullLine?.startsWith('d')?:file.isDirectory
					files.add(ListItem(activity, file.absolutePath, line, isDir, 0, 0, 0))
					super.commandOutput(id, line)
				}

				override fun commandCompleted(id: Int, exitcode: Int) {
					if(files.isEmpty()) callback(path, files)
					else getChildrenCount(files, path, callback)
					super.commandCompleted(id, exitcode)
				}
			}
			runCommand(command)
		}
	}

	private fun getFullLines(path: String, callback: (ArrayList<String>)->Unit) {
		val fullLines = ArrayList<String>()
		val hiddenArgument = if(activity.config.shouldShowHidden()) "-Al " else "-l "
		val cmd = "ls $hiddenArgument$path"

		val command = object: Command(0, cmd) {
			override fun commandOutput(id: Int, line: String) {
				fullLines.add(line)
				super.commandOutput(id, line)
			}
			override fun commandCompleted(id: Int, exitcode: Int) {
				callback(fullLines)
				super.commandCompleted(id, exitcode)
			}
		}
		runCommand(command)
	}

	private fun getChildrenCount(files: ArrayList<ListItem>, path: String, callback: (origPath: String, items: ArrayList<ListItem>)->Unit) {
		val hiddenArgument = if(activity.config.shouldShowHidden()) "-A " else ""
		var cmd = ""
		files.filter {it.isDir}.forEach {cmd += "ls $hiddenArgument${it.path} |wc -l;"}
		cmd = cmd.trimEnd(';') + " | cat"

		val lines = ArrayList<String>()
		val command = object: Command(0, cmd) {
			override fun commandOutput(id: Int, line: String) {
				lines.add(line)
				super.commandOutput(id, line)
			}
			override fun commandCompleted(id: Int, exitcode: Int) {
				files.filter {it.isDir}.forEachIndexed {idx, item ->
					val childrenCount = lines[idx]
					if(childrenCount.areDigitsOnly()) item.children = childrenCount.toInt()
				}
				if(activity.config.getFolderSorting(path) and SORT_BY_SIZE == 0) callback(path, files)
				else getFileSizes(files, path, callback)
				super.commandCompleted(id, exitcode)
			}
		}
		runCommand(command)
	}

	private fun getFileSizes(items: ArrayList<ListItem>, path: String, callback: (origPath: String, items: ArrayList<ListItem>)->Unit) {
		var cmd = ""
		items.filter {!it.isDir}.forEach {cmd += "stat -t ${it.path};"}

		val lines = ArrayList<String>()
		val command = object: Command(0, cmd) {
			override fun commandOutput(id: Int, line: String) {
				lines.add(line)
				super.commandOutput(id, line)
			}
			override fun commandCompleted(id: Int, exitcode: Int) {
				items.filter {!it.isDir}.forEachIndexed {idx, item ->
					var line = lines[idx]
					if(line.isNotEmpty() && line != "0") {
						if(line.length >= item.path.length) {
							line = line.substring(item.path.length).trim()
							val size = line.split(" ")[0]
							if(size.areDigitsOnly()) item.size = size.toLong()
						}
					}
				}
				callback(path, items)
				super.commandCompleted(id, exitcode)
			}
		}
		runCommand(command)
	}

	private fun runCommand(command: Command) {
		try {RootTools.getShell(true).add(command)}
		catch(e: Throwable) {activity.error(e)}
	}

	fun createFileFolder(path: String, isFile: Boolean, callback: (success: Boolean)->Unit) {
		if(!RootTools.isRootAvailable()) {
			activity.toast(R.string.rooted_device_only)
			return
		}
		tryMountAsRW(path) {
			val mountPoint = it
			val targetPath = path.trim('/')
			val mainCommand = if(isFile) "touch" else "mkdir"
			val cmd = "$mainCommand \"/$targetPath\""
			val command = object: Command(0, cmd) {
				override fun commandCompleted(id: Int, exitcode: Int) {
					callback(exitcode == 0)
					mountAsRO(mountPoint)
					super.commandCompleted(id, exitcode)
				}
			}
			runCommand(command)
		}
	}

	private fun mountAsRO(mountPoint: String?) {
		if(mountPoint != null) {
			val cmd = "umount -r \"$mountPoint\""
			val command = object: Command(0, cmd) {}
			runCommand(command)
		}
	}

	//Inspired by Amaze File Manager
	private fun tryMountAsRW(path: String, callback: (mountPoint: String?)->Unit) {
		val mountPoints = ArrayList<String>()
		val cmd = "mount"
		val command = object: Command(0, cmd) {
			override fun commandOutput(id: Int, line: String) {
				mountPoints.add(line)
				super.commandOutput(id, line)
			}
			override fun commandCompleted(id: Int, exitcode: Int) {
				var mountPoint = ""
				var types: String? = null
				for(line in mountPoints) {
					val words = line.split(" ").filter {it.isNotEmpty()}

					if(path.contains(words[2])) {
						if(words[2].length > mountPoint.length) {
							mountPoint = words[2]
							types = words[5]
						}
					}
				}
				if(mountPoint.isNotEmpty() && types != null) {
					if(types.contains("rw")) {
						callback(null)
					} else if(types.contains("ro")) {
						val mountCommand = "mount -o rw,remount $mountPoint"
						mountAsRW(mountCommand) {
							callback(it)
						}
					}
				}
				super.commandCompleted(id, exitcode)
			}
		}
		runCommand(command)
	}

	private fun mountAsRW(cmd: String, callback: (mountPoint: String)->Unit) {
		val command = object: Command(0, cmd) {
			override fun commandOutput(id: Int, line: String) {
				callback(line)
				super.commandOutput(id, line)
			}
		}
		runCommand(command)
	}

	fun deleteFiles(items: ArrayList<ListItem>) {
		if(!RootTools.isRootAvailable()) {
			activity.toast(R.string.rooted_device_only)
			return
		}
		tryMountAsRW(items.first().path) {
			items.forEach {
				val targetPath = it.path.trim('/')
				if(targetPath.isEmpty()) return@forEach
				val mainCommand = if(it.isDir) "rm -rf" else "rm"
				val cmd = "$mainCommand \"/$targetPath\""
				val command = object: Command(0, cmd) {}
				runCommand(command)
			}
		}
	}

	fun copyMoveFiles(items: ArrayList<ListItem>, destination: String, isCopyOperation: Boolean, successes: Int = 0, callback: (Int)->Unit) {
		if(!RootTools.isRootAvailable()) {
			activity.toast(R.string.rooted_device_only)
			return
		}
		val item = items.first()
		val mainCommand = if(isCopyOperation) {
			if(item.isDir) "cp -R" else "cp"
		} else "mv"

		val cmd = "$mainCommand \"${item.path}\" \"$destination\""
		val command = object: Command(0, cmd) {
			override fun commandCompleted(id: Int, exitcode: Int) {
				val newSuccesses = successes + (if(exitcode == 0) 1 else 0)
				if(items.size == 1) {
					callback(newSuccesses)
				} else {
					items.removeAt(0)
					copyMoveFiles(items, destination, isCopyOperation, newSuccesses, callback)
				}
				super.commandCompleted(id, exitcode)
			}
		}
		runCommand(command)
	}
}