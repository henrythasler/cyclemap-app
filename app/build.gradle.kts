import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

fun getApiKey(): String {
    val properties = Properties()
    properties.load(rootProject.file("local.properties").inputStream())
    return "\"${properties.getProperty("MAPBOX_ACCESS_TOKEN")}\""
}

fun getDownloadKey(): String {
    val properties = Properties()
    properties.load(rootProject.file("local.properties").inputStream())
    return "\"${properties.getProperty("MAPBOX_DOWNLOADS_TOKEN")}\""
}

// Function to read the current build number
fun getBuildNumber(): Int {
    val versionFile = rootProject.file("version.properties")
    val properties = Properties()

    if (versionFile.exists()) {
        properties.load(FileInputStream(versionFile))
    }

    return properties.getProperty("buildNumber", "0").toInt()
}

// Function to increment and save the build number
fun incrementBuildNumber(): Int {
    val versionFile = rootProject.file("version.properties")
    val properties = Properties()

    if (versionFile.exists()) {
        properties.load(FileInputStream(versionFile))
    }

    val buildNumber = properties.getProperty("buildNumber", "0").toInt() + 1
    properties.setProperty("buildNumber", buildNumber.toString())
    properties.store(FileOutputStream(versionFile), null)

    return buildNumber
}

fun getCommitHash(): String {
    val process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
    return try {
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

// Function to check if working directory is clean
fun isGitClean(): Boolean {
    val process = Runtime.getRuntime().exec("git status --porcelain")
    return try {
        process.inputStream.bufferedReader().readText().isEmpty()
    } catch (e: Exception) {
        false
    }
}

// Function to get current branch name
fun getGitBranch(): String {
    return try {
        val process = Runtime.getRuntime().exec("git rev-parse --abbrev-ref HEAD")
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

android {
    namespace = "com.henrythasler.cyclemap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.henrythasler.cyclemap"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        val versionMinor = 1
        versionName = "$versionCode.$versionMinor.${getBuildNumber()}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // provide access token
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", getApiKey())
        buildConfigField("String", "MAPBOX_DOWNLOADS_TOKEN", getDownloadKey())

        // Make version info available in BuildConfig
        buildConfigField("int", "BUILD_NUMBER", "${getBuildNumber()}")
        buildConfigField("String", "BRANCH_NAME", "\"${getGitBranch()}\"")
        buildConfigField("String", "COMMIT_HASH", "\"${getCommitHash()}\"")
        buildConfigField("boolean", "GIT_LOCAL_CHANGES", "${!isGitClean()}")
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("String", "BUILD_DATE", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(
            Date()
        )}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
//            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BUILD_TYPE", "\"release\"")
            buildConfigField("boolean", "DEBUG_MODE", "false")
        }
        debug {
            buildConfigField("String", "BUILD_TYPE", "\"debug\"")
            buildConfigField("boolean", "DEBUG_MODE", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Increment build number for release builds
    applicationVariants.all {
        if (buildType.name == "release") {
            incrementBuildNumber()
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.play.services.location)
    implementation(libs.simple.xml)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.constraintlayout)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // mapbox
    implementation(libs.maps.android)
    implementation(libs.maps.compose)
    implementation(libs.mapbox.sdk.turf)
    implementation(libs.mapbox.search.android)
    implementation(libs.mapbox.sdk.services)
}