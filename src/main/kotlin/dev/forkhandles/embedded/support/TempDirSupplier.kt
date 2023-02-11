package dev.forkhandles.embedded.support
import java.io.File
import java.nio.file.Files
import java.util.function.Supplier

/**
 * Supplier for temporary directories.
 *
 * Keeps track of all the temporary directories that were created from this instance, and deletes them
 * when it is closed.
 */
class TempDirSupplier(
        private val prefix: String
) : Supplier<File> {
    override fun get(): File =
            Files.createTempDirectory(prefix).toFile().also { it.deleteOnExit() }
}
