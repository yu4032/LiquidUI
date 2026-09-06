plugins {
    id("com.android.application") version "9.3.0"
    id("com.android.library") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}
android {
    namespace = "com.hellovoid.liquidui"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.hellovoid.liquidui"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug { optimization { enable = true } }
        release { optimization { enable = true } }
    }
    buildFeatures { compose = true }
    packaging { resources { merges += "META-INF/xposed/*" } }
    lint { disable += listOf("BlockedPrivateApi", "SoonBlockedPrivateApi") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":prismal"))
    compileOnly("io.github.libxposed:api:101.0.1")
    implementation("io.github.libxposed:service:101.0.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    testImplementation("junit:junit:4.13.2")
}
base { archivesName.set("LiquidUI") }
