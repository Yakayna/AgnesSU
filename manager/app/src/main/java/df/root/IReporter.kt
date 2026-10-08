package df.root

/**
 * Progress sink for the DirtyFrag engine subprocess — one call per stdout line.
 * The engine is no longer a JNI library, so this is a plain Kotlin callback
 * rather than a native-resolved ABI.
 */
fun interface IReporter {
    fun report(message: String)
}
