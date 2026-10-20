plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.chaoshan"
version = "1.0.0"

// 与 Kotlin 的 jvmTarget 保持一致，避免 Java/Kotlin 目标版本不一致导致构建失败。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
