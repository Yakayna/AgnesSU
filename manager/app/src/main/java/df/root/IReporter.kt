package df.root

/**
 * Progress sink for the DirtyFrag native engine. `libexp.so` caches the
 * `report` method id in JNI_OnLoad (signature `(Ljava/lang/String;)V`) and
 * calls it from the exploit thread for every log line, so this interface's
 * name, package and method must match the native lookup exactly.
 */
interface IReporter {
    fun report(message: String)
}
