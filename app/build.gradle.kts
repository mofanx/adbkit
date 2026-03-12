plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.adbkit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adbkit.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.1"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Extract .so files to nativeLibraryDir on install.
        // Required because libadb.so is an executable (not a JNI library)
        // that we run via ProcessBuilder, so it must exist on disk.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "adbkit-v${variant.versionName}-${variant.buildType.name}.apk"
        }
    }
}

// ── Build ScreenServer DEX for target device ──────────────────────────────────
// Compiles server/src/ScreenServer.java → app/src/main/assets/screen-server.dex
// using android.jar from the SDK and d8 from build-tools.
val serverSrcDir = rootProject.layout.projectDirectory.dir("server/src")
val serverBuildDir = layout.buildDirectory.dir("server")
val assetsDir = layout.projectDirectory.dir("src/main/assets")

tasks.register("compileScreenServer") {
    val srcFile = serverSrcDir.file("ScreenServer.java")
    val classesDir = serverBuildDir.map { it.dir("classes") }
    inputs.file(srcFile)
    outputs.dir(classesDir)

    doLast {
        val sdk = android.sdkDirectory
        val androidJar = file("$sdk/platforms/android-${android.compileSdk}/android.jar")
        require(androidJar.exists()) { "android.jar not found at $androidJar" }

        val outDir = classesDir.get().asFile
        outDir.mkdirs()

        project.exec {
            commandLine(
                "javac",
                "-source", "17", "-target", "17",
                "-bootclasspath", androidJar.absolutePath,
                "-classpath", androidJar.absolutePath,
                "-d", outDir.absolutePath,
                "-Xlint:-options",
                srcFile.asFile.absolutePath
            )
        }
    }
}

tasks.register("dexScreenServer") {
    dependsOn("compileScreenServer")
    val classesDir = serverBuildDir.map { it.dir("classes") }
    val dexDir = serverBuildDir.map { it.dir("dex") }
    inputs.dir(classesDir)
    outputs.dir(dexDir)

    doLast {
        val sdk = android.sdkDirectory
        // Find the latest build-tools version
        val buildToolsDir = file("$sdk/build-tools")
        val latestBt = buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
            ?: error("No build-tools found in $buildToolsDir")
        val d8 = file("${latestBt.absolutePath}/d8")
        require(d8.exists()) { "d8 not found at $d8" }

        val outDir = dexDir.get().asFile
        outDir.mkdirs()

        val classFiles = classesDir.get().asFile.walkTopDown()
            .filter { it.extension == "class" }
            .map { it.absolutePath }
            .toList()

        project.exec {
            commandLine(
                listOf(d8.absolutePath, "--output", outDir.absolutePath, "--min-api", "26") + classFiles
            )
        }
    }
}

tasks.register<Copy>("copyScreenServerDex") {
    dependsOn("dexScreenServer")
    from(serverBuildDir.map { it.dir("dex") }) {
        include("classes.dex")
        rename("classes.dex", "screen-server.dex")
    }
    into(assetsDir)
}

// Hook into the build: ensure DEX is ready before app resources are merged
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn("copyScreenServerDex")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gson)
    debugImplementation(libs.androidx.ui.tooling)
}
