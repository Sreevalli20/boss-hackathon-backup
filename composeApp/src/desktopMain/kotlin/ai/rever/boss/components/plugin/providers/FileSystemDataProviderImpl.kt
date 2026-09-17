package ai.rever.boss.components.plugin.providers

import ai.rever.boss.utils.PluginFileSystemSecurity

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