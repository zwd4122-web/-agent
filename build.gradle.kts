// 根工程：只声明插件版本，不在根工程应用插件。
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("com.android.application") version "9.4.1" apply false
}
