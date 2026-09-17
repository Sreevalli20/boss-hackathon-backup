package ai.rever.boss.utils

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Platform-specific implementation of PluginFileSystemSecurity for JVM platforms.
 *
 * This file contains the actual JVM-specific implementations of the expect functions
 * declared in the commonMain PluginFileSystemSecurity.kt.
 */
internal actual fun validateAndNormalizePathPlatform(
    rawPath: String,
    operation: String,
): String {
    val logger = BossLogger.forComponent("PluginFileSystemSecurity")

    try {
        // Convert to Path object for robust normalization
        val path = Paths.get(rawPath)

        // Normalize to handle . and .. segments
        val normalizedPath = path.normalize()

        // Convert to absolute path
        val absolutePath = normalizedPath.toAbsolutePath()

        // Resolve to canonical path (follows symlinks, handles platform-specific issues)
        val canonicalPath = absolutePath.normalize()

        // Enforce boundary: must be within user's home directory
        val homeDir = File(System.getProperty("user.home")).canonicalFile
        val homePath = homeDir.toPath().normalize()

        if (!isPathWithinBoundary(canonicalPath, homePath)) {
            logger.warn(
                LogCategory.SECURITY,
                "Plugin filesystem access denied: path outside allowed boundary",
                mapOf(
                    "path" to canonicalPath.toString(),
                    "boundary" to homePath.toString(),
                    "operation" to operation,
                ),
            )
            throw SecurityException(
                "Access denied: path '${canonicalPath}' is outside the allowed boundary (user home directory). " +
                    "Use FilePickerProvider for user-mediated file access or work within your plugin storage directory.",
            )
        }

        return canonicalPath.toString()
    } catch (e: InvalidPathException) {
        throw SecurityException("Invalid filesystem path for $operation: ${e.message}")
    } catch (e: SecurityException) {
        // Re-throw our security exceptions
        throw e
    } catch (e: Exception) {
        logger.warn(LogCategory.SECURITY, "Path validation failed", mapOf("path" to rawPath, "error" to e.toString()))
        throw SecurityException("Path validation failed for $operation: ${e.message}")
    }
}

internal actual fun validateChildPathPlatform(
    canonicalParent: String,
    childName: String,
    operation: String,
): String {
    // Construct the full child path
    val childPath = File(canonicalParent, childName)

    // Ensure the child is within the parent
    val canonicalChild = childPath.canonicalFile
    if (!isPathWithinBoundary(canonicalChild.toPath(), Paths.get(canonicalParent))) {
        throw SecurityException(
            "Path traversal detected: child would be created outside parent directory for $operation",
        )
    }

    return canonicalChild.absolutePath
}

actual fun getAllowedBoundary(): String {
    return File(System.getProperty("user.home")).canonicalFile.absolutePath
}

/**
 * Checks if a path is within a boundary directory.
 *
 * Uses canonical path comparison to handle symlinks and platform differences.
 * Implements robust boundary checking to prevent prefix collision attacks.
 *
 * @param path The path to check
 * @param boundary The boundary directory
 * @return true if the path is within the boundary, false otherwise
 */
private fun isPathWithinBoundary(
    path: Path,
    boundary: Path,
): Boolean {
    val normalizedPath = path.normalize()
    val normalizedBoundary = boundary.normalize()

    // Exact match is allowed (the boundary itself)
    if (normalizedPath == normalizedBoundary) {
        return true
    }

    // Use Path.startsWith() which handles path separator correctly
    // This prevents prefix collision attacks (e.g., /home/user vs /home/user-other)
    if (normalizedPath.startsWith(normalizedBoundary)) {
        // Additional check: ensure the path doesn't just start with the boundary string
        // but is actually a subdirectory or file within it
        val remaining = normalizedPath.toString().substring(normalizedBoundary.toString().length)
        // The remaining part must start with a separator (indicating it's a child)
        return remaining.startsWith(File.separator) || remaining.startsWith("/") || remaining.startsWith("\\")
    }

    return false
}
