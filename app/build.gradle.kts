plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

val apiBaseUrl = providers
    .gradleProperty("HOGARSCAN_API_BASE_URL")
    .orElse("")
    .get()

require(apiBaseUrl.none { it.code < 32 || it.code == 127 }) {
    "HOGARSCAN_API_BASE_URL contiene caracteres de control."
}

val apiBaseUrlLiteral = apiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .let { "\"$it\"" }

room {
    schemaDirectory("$projectDir/schemas")
}

android {

    namespace = "com.example.hogarscan"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.hogarscan"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "API_BASE_URL",
            apiBaseUrlLiteral
        )
    }

    buildTypes {
        release {
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
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.androidsvg)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    androidTestImplementation(libs.androidx.room.testing)

    testImplementation(libs.kotlinx.coroutines.test)
}
