plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun readFujiNetRuntimeVersion(): String {
    val versionHeader = rootProject.file("tools/fujinet/work/fujinet-firmware/include/version.h")
    if (!versionHeader.isFile) {
        return "fujinet-runtime-v1"
    }
    val match = Regex("""#define\s+FN_VERSION_FULL\s+"([^"]+)"""")
        .find(versionHeader.readText())
    return match?.groupValues?.get(1) ?: "fujinet-runtime-v1"
}

val fujiNetRuntimeVersion = readFujiNetRuntimeVersion()

fun readAdamemVersion(): String {
    val sourceScript = rootProject.file("tools/adamem/build-adamem-core.sh")
    if (!sourceScript.isFile) {
        return "Unknown"
    }
    val scriptText = sourceScript.readText()
    val branch = Regex("""SOURCE_BRANCH="([^"]+)"""")
        .find(scriptText)
        ?.groupValues
        ?.get(1)
    val commit = Regex("""SOURCE_COMMIT="([^"]+)"""")
        .find(scriptText)
        ?.groupValues
        ?.get(1)
    val shortCommit = commit?.take(8)
    return when {
        !branch.isNullOrBlank() && !shortCommit.isNullOrBlank() -> "$branch ($shortCommit)"
        !shortCommit.isNullOrBlank() -> shortCommit
        !branch.isNullOrBlank() -> branch
        else -> "Unknown"
    }
}

val adamemVersion = readAdamemVersion()

val prepareAdamemCore by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Stages the ADAMEm emulator core source tree from the local checkout."
    workingDir = rootProject.projectDir
    commandLine("bash", rootProject.file("tools/adamem/build-adamem-core.sh").absolutePath)
    inputs.file(rootProject.file("tools/adamem/build-adamem-core.sh"))
    outputs.dir(project.file("src/main/cpp-generated/adamem"))
}

// Optional dev override: -PadamAbi=arm64-v8a builds a single ABI for fast
// iteration. Unset => all four packaged ABIs (release/default).
val adamAbi: String? = (project.findProperty("adamAbi") as String?)?.takeIf { it.isNotBlank() }
val fujinetAbiArgs: List<String> =
    if (adamAbi != null) listOf("--abi", adamAbi) else listOf("--all-abis")

val prepareFujiNetRuntime by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Builds the FujiNet ADAM Android runtime for the packaged ABIs."
    workingDir = rootProject.projectDir
    commandLine(listOf("bash", rootProject.file("tools/fujinet/build-fujinet.sh").absolutePath) + fujinetAbiArgs)
    inputs.file(rootProject.file("tools/fujinet/build-fujinet.sh"))
    inputs.dir(rootProject.file("tools/fujinet/patches"))
    inputs.dir(rootProject.file("tools/fujinet/support"))
    outputs.dir(project.file("src/main/assets-generated/fujinet"))
    outputs.dir(project.file("src/main/jniLibs-generated"))
}

tasks.configureEach {
    if (name.contains("Release") || name == "preBuild") {
        dependsOn(prepareFujiNetRuntime)
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareAdamemCore)
}

tasks.matching { task ->
    task.name.startsWith("configureCMake") || task.name.startsWith("buildCMake")
}.configureEach {
    dependsOn(prepareAdamemCore)
}

tasks.matching { task ->
    task.name.startsWith("merge") && (
        task.name.endsWith("Assets")
            || task.name.endsWith("JniLibFolders")
            || task.name.endsWith("NativeLibs")
    )
}.configureEach {
    dependsOn(prepareFujiNetRuntime)
}

android {
    namespace = "online.fujinet.go.adam"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "online.fujinet.go.adam"
        minSdk = 26
        targetSdk = 35
        versionCode = 4 
        versionName = "0.9.0"
        buildConfigField("String", "ADAMEM_VERSION", "\"${adamemVersion}\"")
        buildConfigField("String", "FUJINET_RUNTIME_VERSION", "\"${fujiNetRuntimeVersion}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
        ndk {
            if (adamAbi != null) {
                abiFilters += adamAbi
            } else {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    sourceSets {
        getByName("main") {
            // assets-generated/{fujinet,adamem}/... are staged by the tools/ scripts.
            assets.srcDir("src/main/assets-generated")
            jniLibs.srcDir("src/main/jniLibs-generated")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation(libs.junit)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
    testImplementation(libs.androidx.lifecycle.viewmodel.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
