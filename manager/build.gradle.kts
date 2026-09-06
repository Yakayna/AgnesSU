plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

extra["androidMinSdkVersion"] = 26
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidBuildToolsVersion"] = "36.1.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["managerVersionCode"] = 30000 + getGitCommitCount() + 700
extra["managerVersionName"] = getManagerVersionName()

fun getGitCommitCount(): Int {
    return providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
}

// AgnesSU version name: controlled via manager/version.txt so releases are named
// after the base version (matching ReSukiSU) without the auto commit-count suffix.
// Bump iterations of the same base by editing the file to e.g. "v4.1.0_2", "v4.1.0_3".
// Falls back to git describe if the file is missing/empty.
fun getManagerVersionName(): String {
    val f = rootProject.file("version.txt")
    if (f.exists()) {
        val v = f.readText().trim()
        if (v.isNotEmpty()) return v
    }
    return getGitDescribe()
}

fun getGitDescribe(): String {
    return providers.exec {
        commandLine("git", "describe", "--tags", "--always", "--abbrev=0")
    }.standardOutput.asText.get().trim()
}
