// 说明：AGP 9.x 自带 Kotlin 编译支持（内置 kotlin 扩展），
// 因此这里不再额外应用 org.jetbrains.kotlin.android 插件，否则会与内置扩展重名冲突。
plugins {
    id("com.android.application")
}

android {
    namespace = "com.chaoshan.travel.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chaoshan.travel.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    // Agent 能力全部来自 :agents 模块，app 模块只负责交互展示
    implementation(project(":agents"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.8.0")

    testImplementation("junit:junit:4.13.2")
}
