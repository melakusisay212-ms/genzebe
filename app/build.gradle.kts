plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.genzebe.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.genzebe.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Two builds of the same app:
    //  auto  - reads SMS by itself (declares RECEIVE_SMS and READ_SMS)
    //  share - declares no SMS permission; you share or paste a message into the app
    // Google Play Protect blocks installing an app that declares SMS permissions when it comes
    // from a browser, chat app or file manager. The "share" build avoids that.
    flavorDimensions += "channel"
    productFlavors {
        create("auto") {
            dimension = "channel"
            versionNameSuffix = "-auto"
            buildConfigField("boolean", "AUTO_READ", "true")
        }
        create("share") {
            dimension = "channel"
            versionNameSuffix = "-share"
            buildConfigField("boolean", "AUTO_READ", "false")
        }
    }

    // The release key comes from environment variables (set by the GitHub workflow).
    // Without them a local release build is signed with the debug key so it still installs.
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Kept off on purpose: readable, unobfuscated builds are less likely to look suspicious.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
}
