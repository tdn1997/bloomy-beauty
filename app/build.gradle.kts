import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val presentationBuild = providers.gradleProperty("presentation").orNull == "true"
val demoAdmin = Properties().apply {
    val config = rootProject.file(if (presentationBuild) ".local/presentation-admin.properties" else ".local/admin-demo.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}


android {
    namespace = "com.example.bloomybeauty"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bloomybeauty"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        for (field in listOf("EMAIL", "HASH", "SALT")) buildConfigField("String", "DEMO_ADMIN_$field", "\"\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
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
