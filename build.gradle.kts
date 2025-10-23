// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.8.0" apply false
    id("com.android.test") version "8.8.0" apply false
    id("androidx.baselineprofile") version "1.2.4" apply false  // 更新版本
    val kotlinVersion = "1.9.22"  // 使用稳定版本，而不是RC版本
    id("org.jetbrains.kotlin.android") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version kotlinVersion apply false
    id("com.google.devtools.ksp") version "$kotlinVersion-1.0.17" apply false
    id("com.mikepenz.aboutlibraries.plugin") version "11.1.3" apply false  // 使用兼容版本
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:all")
}