package ai.rever.boss.utils

/**
 * Reveal [path] in the OS file manager, selecting the file where supported
 * (macOS Finder, Windows Explorer) or opening its containing folder (Linux).
 * No-op when [path] is blank; returns a failure (and logs) if the OS command
 * can't be launched.
 *
 * This is the single canonical reveal implementation — `FileSystemUtils.revealInFolder`
 * and `FileSystemDataProviderImpl.revealInFileManager` delegate here so the OS-specific
 * command lives in exactly one place.
 *
 * Security: Validation is handled by the caller (FileSystemDataProviderImpl) which
 * calls PluginFileSystemSecurity.validateAndNormalizePath before this function.
 */
expect fun revealInFileManager(path: String): Result<Unit>

/** Platform-appropriate label for the reveal action. */
expect fun revealInFileManagerLabel(): String
