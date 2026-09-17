package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.FileNodeData
import ai.rever.boss.utils.PluginFileSystemSecurity
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import ai.rever.boss.components.plugin.panels.left_top.scanDirectory
import ai.rever.boss.components.plugin.panels.left_top.scanDirectoryWithDepth
import ai.rever.boss.components.plugin.panels.left_top.directoryHasChildren

private val logger = BossLogger.forComponent("FileSystemDataProvider")

/**
 * Desktop-specific implementations of path validation functions.
 *
 * These are JVM-specific implementations that delegate to PluginFileSystemSecurity
 * for actual security validation.
 */
internal actual fun validateFileSystemPath(path: String, operation: String): String {
    return PluginFileSystemSecurity.validateAndNormalizePath(path, operation)
}

internal actual fun validateChildFileSystemPath(parentPath: String, childName: String, operation: String): String {
    return PluginFileSystemSecurity.validateChildPath(parentPath, childName, operation)
}

/**
 * Desktop-specific implementations of file operations.
 */
internal actual fun scanDirectoryPlatform(path: String): FileNodeData? = scanDirectory(path)

internal actual fun scanDirectoryPlatform(path: String, showHidden: Boolean): FileNodeData? = scanDirectory(path, showHidden)

internal actual fun scanDirectoryWithDepthPlatform(path: String, maxDepth: Int, startDepth: Int): FileNodeData? =
    scanDirectoryWithDepth(path, maxDepth, startDepth)

internal actual fun scanDirectoryWithDepthPlatform(path: String, maxDepth: Int, startDepth: Int, showHidden: Boolean): FileNodeData? =
    scanDirectoryWithDepth(path, maxDepth, startDepth, showHidden)

internal actual fun directoryHasChildrenPlatform(path: String): Boolean = directoryHasChildren(path)

internal actual fun directoryHasChildrenPlatform(path: String, showHidden: Boolean): Boolean = directoryHasChildren(path, showHidden)

internal actual fun createFilePlatform(parentPath: String, fileName: String): Result<String> {
    return try {
        val validatedPath = validateChildFileSystemPath(parentPath, fileName, "createFile")
        val newFile = File(validatedPath)

        val parentDir = newFile.parentFile
        if (parentDir != null && (!parentDir.exists() || !parentDir.isDirectory)) {
            return Result.failure(IllegalArgumentException("Parent directory does not exist: $parentPath"))
        }

        if (newFile.exists()) {
            return Result.failure(IllegalStateException("File already exists: ${newFile.absolutePath}"))
        }

        val created = newFile.createNewFile()
        if (created) {
            Result.success(newFile.absolutePath)
        } else {
            Result.failure(IllegalStateException("Failed to create file: ${newFile.absolutePath}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal actual fun createFolderPlatform(parentPath: String, folderName: String): Result<String> {
    return try {
        val validatedPath = validateChildFileSystemPath(parentPath, folderName, "createFolder")
        val newFolder = File(validatedPath)

        val parentDir = newFolder.parentFile
        if (parentDir != null && (!parentDir.exists() || !parentDir.isDirectory)) {
            return Result.failure(IllegalArgumentException("Parent directory does not exist: $parentPath"))
        }

        if (newFolder.exists()) {
            return Result.failure(IllegalStateException("Folder already exists: ${newFolder.absolutePath}"))
        }

        val created = newFolder.mkdir()
        if (created) {
            Result.success(newFolder.absolutePath)
        } else {
            Result.failure(IllegalStateException("Failed to create folder: ${newFolder.absolutePath}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal actual fun deletePlatform(path: String): Result<Unit> {
    return try {
        val validatedPath = validateFileSystemPath(path, "delete")
        val file = File(validatedPath)

        // Note: We don't check exists() first to avoid race conditions.
        // delete() and deleteRecursively() handle non-existent files gracefully.
        val deleted =
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }

        if (deleted) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to delete (file may not exist or is locked): $path"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal actual fun renamePlatform(path: String, newName: String): Result<String> {
    return try {
        val validatedPath = validateFileSystemPath(path, "rename")
        val file = File(validatedPath)
        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("File or folder does not exist: $path"))
        }

        val parentDir =
            file.parentFile
                ?: return Result.failure(IllegalStateException("Cannot determine parent directory"))

        val validatedNewPath = validateChildFileSystemPath(parentDir.absolutePath, newName, "rename")
        val newFile = File(validatedNewPath)

        if (newFile.exists()) {
            return Result.failure(IllegalStateException("A file or folder with that name already exists"))
        }

        val renamed = file.renameTo(newFile)
        if (renamed) {
            Result.success(newFile.absolutePath)
        } else {
            Result.failure(IllegalStateException("Failed to rename: $path"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal actual fun copyToClipboardPlatform(text: String): Result<Unit> =
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
        Result.success(Unit)
    } catch (e: Exception) {
        logger.warn(LogCategory.FILE, "Failed to copy to clipboard", error = e)
        Result.failure(e)
    }

internal actual fun writeFilePlatform(path: String, content: String): Result<Unit> {
    return try {
        val validatedPath = validateFileSystemPath(path, "writeFile")
        val file = File(validatedPath)

        // Ensure parent directory exists
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        file.writeText(content)
        Result.success(Unit)
    } catch (e: Exception) {
        logger.warn(LogCategory.FILE, "Failed to write file", mapOf("path" to path), error = e)
        Result.failure(e)
    }
}

internal actual fun readFilePlatform(path: String): Result<String> {
    return try {
        val validatedPath = validateFileSystemPath(path, "readFile")
        val file = File(validatedPath)

        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("File does not exist: $path"))
        }
        Result.success(file.readText())
    } catch (e: Exception) {
        logger.warn(LogCategory.FILE, "Failed to read file", mapOf("path" to path), error = e)
        Result.failure(e)
    }
}

internal actual fun getDownloadsDirectoryPlatform(): String {
    val homeDir = System.getProperty("user.home")
    val downloadsDir = File(homeDir, "Downloads")
    return if (downloadsDir.exists()) downloadsDir.absolutePath else homeDir
}

internal actual fun getHomeDirectoryPlatform(): String = System.getProperty("user.home")