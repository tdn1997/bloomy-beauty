import java.util.Properties
import com.google.gms.googleservices.GoogleServicesTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

abstract class GenerateFirebaseConfig : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templateFile: RegularFileProperty

    @get:Input
    abstract val apiKey: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val key = apiKey.get().trim()
        require(key.isNotEmpty()) {
            "Set FIREBASE_API_KEY in the environment or in the root .env.local file (see .env.example)."
        }
        require(key.matches(Regex("[A-Za-z0-9_-]+"))) {
            "FIREBASE_API_KEY must contain only letters, digits, underscores, and hyphens; do not quote it."
        }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(templateFile.get().asFile.readText().replace("__FIREBASE_API_KEY__", key))
        }
    }
}

val presentationBuild = providers.gradleProperty("presentation").orNull == "true"
val cloudEmulatorBuild = providers.gradleProperty("cloudEmulator").orNull == "true"
require(!cloudEmulatorBuild || (!presentationBuild && providers.gradleProperty("qaRelease").orNull != "true")) {"Cloud emulator is a separate build source."}
val qaReleaseBuild = providers.gradleProperty("qaRelease").orNull == "true"
// The supplied Firebase config only registers the normal application ID.
// Keep the isolated presentation and QA builds offline.
if (!presentationBuild && !qaReleaseBuild && !cloudEmulatorBuild) {
    apply(plugin = "com.google.gms.google-services")
    val localApiKey = providers.fileContents(rootProject.layout.projectDirectory.file(".env.local"))
        .asText.map { text ->
            Properties().apply { text.reader().use { load(it) } }
                .getProperty("FIREBASE_API_KEY", "")
        }
    val generateFirebaseConfig = tasks.register<GenerateFirebaseConfig>("generateFirebaseConfig") {
        templateFile.set(layout.projectDirectory.file("google-services.json.template"))
        apiKey.set(providers.environmentVariable("FIREBASE_API_KEY").orElse(localApiKey).orElse(""))
        outputFile.set(layout.buildDirectory.file("generated/firebase/google-services.json"))
    }
    androidComponents.onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
        tasks.named<GoogleServicesTask>("process${variantName}GoogleServices") {
            dependsOn(generateFirebaseConfig)
            googleServicesJsonFiles.set(generateFirebaseConfig.flatMap { it.outputFile }.map { listOf(it.asFile) })
        }
    }
}
val demoAdmin = Properties().apply {
    val config = rootProject.file(if (presentationBuild) ".local/presentation-admin.properties" else ".local/admin-demo.properties")
    if (!cloudEmulatorBuild && config.exists()) config.inputStream().use { load(it) }
}


android {
    namespace = "com.example.bloomybeauty"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bloomybeauty"
        buildConfigField("boolean", "CLOUD_EMULATOR", "$cloudEmulatorBuild")
        manifestPlaceholders["localCareEnabled"] = (!cloudEmulatorBuild).toString()
        buildConfigField("boolean", "LOCAL_COUPONS_ENABLED", "false")
        buildConfigField("boolean", "FIREBASE_ENABLED", "${!presentationBuild && !qaReleaseBuild && !cloudEmulatorBuild}")
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        for (field in listOf("EMAIL", "HASH", "SALT")) buildConfigField("String", "DEMO_ADMIN_$field", "\"\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (cloudEmulatorBuild) {
                applicationIdSuffix=".cloud";versionNameSuffix="-cloud-emulator"
                resValue("string","app_name","Bloomy Beauty Cloud (test)")
            }
            buildConfigField("boolean", "LOCAL_COUPONS_ENABLED", "$presentationBuild")
            if (presentationBuild) {
                check(demoAdmin.getProperty("email", "").isNotBlank()) {
                    "Run python3 scripts/create-demo-admin.py --presentation first"
                }
                applicationIdSuffix = ".demo"
                versionNameSuffix = "-demo"
            }
            for (field in listOf("EMAIL", "HASH", "SALT")) {
                val value = demoAdmin.getProperty(field.lowercase(), "")
                require(value.matches(Regex("[A-Za-z0-9@._+/=-]*")))
                buildConfigField("String", "DEMO_ADMIN_$field", "\"$value\"")
            }
        }
        release {
            buildConfigField("boolean", "LOCAL_COUPONS_ENABLED", "$qaReleaseBuild")
            // Opt-in installable release for QA, isolated from the user's app data.
            // The normal release remains unsigned and uses the production application ID.
            if (providers.gradleProperty("qaRelease").orNull == "true") {
                applicationIdSuffix = ".qa"
                versionNameSuffix = "-qa"
                signingConfig = signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation("com.google.firebase:firebase-auth")
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    // Google Play services pulls Fragment 1.1; Activity Result requires 1.3+.
    implementation(libs.androidx.fragment)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { if(cloudEmulatorBuild) it.enable=false }
}
