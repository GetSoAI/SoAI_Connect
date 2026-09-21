import java.util.Properties
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

val localPropertiesFile = rootProject.file("local.properties")

val localProperties = Properties().apply {
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val releaseStoreFile = localProperties.getProperty("soai.release.storeFile").orEmpty()
val releaseStorePassword = localProperties.getProperty("soai.release.storePassword").orEmpty()
val releaseKeyAlias = localProperties.getProperty("soai.release.keyAlias").orEmpty()
val releaseKeyPassword = localProperties.getProperty("soai.release.keyPassword").orEmpty()

val releaseSigningConfigured = releaseStoreFile.isNotBlank() &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()

val releaseSigningRequirement = "Release signing material is unavailable. Provide soai.release.storeFile, " +
    "soai.release.storePassword, soai.release.keyAlias, and soai.release.keyPassword in " +
    "soai_connect/android/local.properties. This file holds signing secrets and is never tracked in git."

val soaiConnectVersion = rootProject.file("../VERSION").readText().trim()

val thirdPartyNotices = rootProject.file("../licenses/ANDROID-THIRD-PARTY-LICENSES.txt")

fun releaseVersionCode(versionName: String): Int {
    val components = versionName.split(".")
    if (components.size != 3) {
        throw GradleException("SoAI Connect version must be MAJOR.MINOR.PATCH, not '$versionName'.")
    }
    val numbers = components.map { component ->
        component.toIntOrNull()
            ?: throw GradleException("SoAI Connect version component '$component' is not a number.")
    }
    val (major, minor, patch) = numbers
    if (numbers.any { number -> number < 0 }) {
        throw GradleException("SoAI Connect version components must not be negative.")
    }
    if (minor > 99 || patch > 99) {
        throw GradleException("SoAI Connect minor and patch versions must stay below 100.")
    }
    return major * 10000 + minor * 100 + patch
}

android {
    namespace = "com.soai.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.soai.android"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseVersionCode(soaiConnectVersion)
        versionName = soaiConnectVersion
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("com.google.android.material:material:1.14.0")

    implementation("androidx.core:core:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}

tasks.matching { task -> task.name == "packageRelease" || task.name == "packageReleaseBundle" }
    .configureEach {
        doFirst {
            if (!releaseSigningConfigured) {
                throw GradleException(releaseSigningRequirement)
            }
        }
    }

abstract class StageThirdPartyNotices : DefaultTask() {

    @get:InputFile
    abstract val notices: RegularFileProperty

    @get:OutputDirectory
    abstract val generatedResources: DirectoryProperty

    @TaskAction
    fun stage() {
        val rawDirectory = File(generatedResources.get().asFile, "raw")
        rawDirectory.mkdirs()
        notices.get().asFile.copyTo(File(rawDirectory, "licenses.txt"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar(Char::uppercaseChar)
        val stageNotices = tasks.register<StageThirdPartyNotices>("stage${variantName}ThirdPartyNotices") {
            notices.set(thirdPartyNotices)
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            stageNotices,
            StageThirdPartyNotices::generatedResources
        )
    }
}

tasks.register("exportReleaseRuntimeDependencies") {
    val coordinates = configurations.named("releaseRuntimeClasspath").map { configuration ->
        configuration.incoming.resolutionResult.allComponents
            .filter { component -> component.id is ModuleComponentIdentifier }
            .mapNotNull { component -> component.moduleVersion }
            .map { module -> "${module.group}:${module.name}:${module.version}" }
            .distinct()
            .sorted()
    }
    val report = layout.buildDirectory.file("reports/soai/release-runtime-dependencies.txt")
    outputs.file(report)
    doLast {
        val file = report.get().asFile
        file.parentFile.mkdirs()
        file.writeText(coordinates.get().joinToString(separator = "\n", postfix = "\n"))
    }
}
