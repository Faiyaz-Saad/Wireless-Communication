import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0" // Serialization plugin
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            // Compose for Android
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            // Lifecycle - Android only
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            /// Android client - use OkHttp engine for stability
            implementation("io.ktor:ktor-client-okhttp:2.3.12")
            implementation("io.ktor:ktor-client-websockets:2.3.12")
            // No server on Android
        }
        commonMain.dependencies {
            //Previous dependencies
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            // Coroutines + Serialization
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            // Ktor core client
            implementation("io.ktor:ktor-client-core:2.3.12")
        }
        iosMain.dependencies {
            // iOS specific client
            implementation("io.ktor:ktor-client-darwin:2.3.12")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            // Compose Desktop
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // Desktop (Windows/Linux/Mac) specific client
            implementation("io.ktor:ktor-client-cio:2.3.12")
            // Ktor Server dependencies for desktop
            implementation("io.ktor:ktor-server-netty:2.3.12")
            implementation("io.ktor:ktor-server-websockets:2.3.12")
            implementation("io.ktor:ktor-server-core:2.3.12")
            implementation("io.ktor:ktor-server-host-common:2.3.12")
        }
    }
}

android {
    namespace = "org.example.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.example.project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Wireless Communication"
            packageVersion = "1.0.0"
            includeAllModules = true
        }
    }
}
