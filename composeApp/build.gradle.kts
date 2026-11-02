import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:${libs.versions.compose.get()}")
                implementation("org.jetbrains.compose.foundation:foundation:${libs.versions.compose.get()}")
                implementation("org.jetbrains.compose.material3:material3:${libs.versions.composeMaterial3.get()}")
                implementation("org.jetbrains.compose.ui:ui:${libs.versions.compose.get()}")
                implementation("org.jetbrains.compose.material:material-icons-extended:${libs.versions.composeIcons.get()}")
                implementation("org.jetbrains.compose.components:components-resources:${libs.versions.compose.get()}")
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.markdown.renderer)
                implementation(libs.markdown.renderer.m3)
                implementation(libs.multiplatform.settings)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
            }
        }

        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.okhttp)
            }
        }

        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.security.crypto)
                implementation("com.alphacephei:vosk-android:0.3.75")
            }
        }

        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation("com.alphacephei:vosk:0.3.45")
                implementation("net.java.dev.jna:jna:5.13.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.nanobot.client.resources"
}

android {
    namespace = "com.nanobot.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nanobot.client"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}


compose.desktop {
    application {
        mainClass = "com.nanobot.client.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "clawd"
            packageVersion = "0.1.0"
            description = "Clawd gateway client"
            vendor = "clawd"
            linux {
                iconFile.set(project.file("icons/nanobot.png"))
            }
        }
        buildTypes {
            release {
                // ProGuard breaks okhttp/okio bytecode (VerifyError on Okio__JvmOkioKt
                // in the flattened release jar -> every HTTP request dies silently,
                // UI stuck on "Connecting"). Minification gives little for a desktop
                // app and is disabled to keep the class files valid.
                proguard {
                    isEnabled.set(false)
                    configurationFiles.from(project.file("desktop-rules.pro"))
                }
            }
        }
    }
}
