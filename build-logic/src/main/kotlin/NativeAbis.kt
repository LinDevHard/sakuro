import java.io.File

/** Native build settings shared by the vendored C libraries. */
object NativeAbis {
    /**
     * Phones/tablets (64- and 32-bit ARM) and x86_64 emulators/Chromebooks. 32-bit x86
     * devices are practically gone; dropping it saves a full native build and APK size.
     */
    val ALL = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    /**
     * Strips the checkout path from file names baked into binaries (assert messages,
     * `__FILE__`), so builds from different directories (CI vs F-Droid) are byte-identical.
     */
    fun reproducibleFlags(rootDir: File): List<String> =
        // The compiler may see the checkout through a symlink (macOS /tmp → /private/tmp), so map
        // every spelling of the root: absolute, canonical and the canonical one without /private.
        listOf(rootDir.absolutePath, rootDir.canonicalPath, rootDir.canonicalPath.removePrefix("/private"))
            .distinct()
            .flatMap { listOf("-ffile-prefix-map=$it=.", "-fdebug-prefix-map=$it=.") }
}
