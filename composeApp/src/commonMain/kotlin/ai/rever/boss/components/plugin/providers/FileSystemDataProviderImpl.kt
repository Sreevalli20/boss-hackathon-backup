package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.plugin.api.FileNodeData
import ai.rever.boss.plugin.api.FileSystemDataProvider
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.utils.revealInFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Implementation of FileSystemDataProvider that wraps platform-specific file operations.
 * This allows plugins to access file system without direct platform coupling.
 *
 * Security validation is handled through platform-specific implementations in desktopMain.
 */
class FileSystemDataProviderImpl : FileSystemDataProvider {
    private val logger = BossLogger.forComponent("FileSystemDataProvider")
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override suspend fun scanDirectory(path: String): FileNodeData? =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val validatedPath = validateFileSystemPath(path, "scanDirectory")
            scanDirectoryPlatform(validatedPath, false)
        }

    override suspend fun scanDirectoryWithDepth(
        path: String,
        maxDepth: Int,
        startDepth: Int,
    ): FileNodeData? =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val validatedPath = validateFileSystemPath(path, "scanDirectoryWithDepth")
            scanDirectoryWithDepthPlatform(validatedPath, maxDepth, startDepth, false)
        }

    override fun directoryHasChildren(path: String): Boolean {
        val validatedPath = validateFileSystemPath(path, "directoryHasChildren")
        return directoryHasChildrenPlatform(validatedPath, false)
    }

    // This host honors the showHidden flag on the read-side scan overloads
    // (api >= 1.0.66, the first published release with the opt-in).
    // Plugins check this before relying on the flag.
    override val supportsHiddenEntries: Boolean get() = true

    override suspend fun scanDirectory(
        path: String,
        showHidden: Boolean,
    ): FileNodeData? =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val validatedPath = validateFileSystemPath(path, "scanDirectory")
            scanDirectoryPlatform(validatedPath, showHidden)
        }

    override suspend fun scanDirectoryWithDepth(
        path: String,
        maxDepth: Int,
        startDepth: Int,
        showHidden: Boolean,
    ): FileNodeData? =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val validatedPath = validateFileSystemPath(path, "scanDirectoryWithDepth")
            scanDirectoryWithDepthPlatform(validatedPath, maxDepth, startDepth, showHidden)
        }

    override fun directoryHasChildren(
        path: String,
        showHidden: Boolean,
    ): Boolean {
        val validatedPath = validateFileSystemPath(path, "directoryHasChildren")
        return directoryHasChildrenPlatform(validatedPath, showHidden)
    }

    override fun openFile(
        path: String,
        windowId: String,
    ) {
        val validatedPath = validateFileSystemPath(path, "openFile")
        ioScope.launch {
            FileEventBus.openFile(validatedPath, sourceWindowId = windowId)
        }
    }

    override suspend fun createFile(
        parentPath: String,
        fileName: String,
    ): Result<String> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            createFilePlatform(parentPath, fileName)
        }
    }

    override suspend fun createFolder(
        parentPath: String,
        folderName: String,
    ): Result<String> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            createFolderPlatform(parentPath, folderName)
        }
    }

    override suspend fun delete(path: String): Result<Unit> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            deletePlatform(path)
        }
    }

    override suspend fun rename(
        path: String,
        newName: String,
    ): Result<String> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            renamePlatform(path, newName)
        }
    }

    override fun revealInFileManager(path: String): Result<Unit> {
        // Security: Validate path before revealing in file manager
        val validatedPath = try {
            validateFileSystemPath(path, "revealInFileManager")
        } catch (e: SecurityException) {
            logger.warn(LogCategory.SECURITY, "Reveal in file manager denied: path outside allowed boundary", mapOf("path" to path))
            return Result.failure(e)
        }
        return revealInFileManager(validatedPath)
    }

    override fun copyToClipboard(text: String): Result<Unit> = copyToClipboardPlatform(text)

    override suspend fun writeFile(
        path: String,
        content: String,
    ): Result<Unit> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            writeFilePlatform(path, content)
        }
    }

    override suspend fun readFile(path: String): Result<String> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            readFilePlatform(path)
        }
    }

    override fun getDownloadsDirectory(): String = getDownloadsDirectoryPlatform()

    override fun getHomeDirectory(): String = getHomeDirectoryPlatform()
}

/**
 * Platform-specific path validation for filesystem operations.
 * Implemented in desktopMain for JVM platforms with PluginFileSystemSecurity.
 */
internal expect fun validateFileSystemPath(path: String, operation: String): String

/**
 * Platform-specific child path validation for create operations.
 * Implemented in desktopMain for JVM platforms with PluginFileSystemSecurity.
 */
internal expect fun validateChildFileSystemPath(parentPath: String, childName: String, operation: String): String

/**
 * Platform-specific implementations of file operations.
 * Implemented in desktopMain for JVM platforms.
 */
internal expect fun scanDirectoryPlatform(path: String): FileNodeData?
internal expect fun scanDirectoryPlatform(path: String, showHidden: Boolean): FileNodeData?
internal expect fun scanDirectoryWithDepthPlatform(path: String, maxDepth: Int, startDepth: Int): FileNodeData?
internal expect fun scanDirectoryWithDepthPlatform(path: String, maxDepth: Int, startDepth: Int, showHidden: Boolean): FileNodeData?
internal expect fun directoryHasChildrenPlatform(path: String): Boolean
internal expect fun directoryHasChildrenPlatform(path: String, showHidden: Boolean): Boolean
internal expect fun createFilePlatform(parentPath: String, fileName: String): Result<String>
internal expect fun createFolderPlatform(parentPath: String, folderName: String): Result<String>
internal expect fun deletePlatform(path: String): Result<Unit>
internal expect fun renamePlatform(path: String, newName: String): Result<String>
internal expect fun copyToClipboardPlatform(text: String): Result<Unit>
internal expect fun writeFilePlatform(path: String, content: String): Result<Unit>
internal expect fun readFilePlatform(path: String): Result<String>
internal expect fun getDownloadsDirectoryPlatform(): String
internal expect fun getHomeDirectoryPlatform(): String

/**
 * Platform-specific path validation for filesystem operations.
 * Implemented in desktopMain for JVM platforms with PluginFileSystemSecurity.
 */
internal expect fun validateFileSystemPath(path: String, operation: String): String

/**
 * Platform-specific child path validation for create operations.
 * Implemented in desktopMain for JVM platforms with PluginFileSystemSecurity.
 */
internal expect fun validateChildFileSystemPath(parentPath: String, childName: String, operation: String): String
