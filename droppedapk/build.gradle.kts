plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.abxoverflow.droppedapk"
    compileSdk = 36

    signingConfigs {
        create("sdkLibrarySigningConfig") {
            storeFile = file("abxdroppedapk.keystore")
            storePassword = "abxdroppedapk"
            keyAlias = "abxdroppedapk"
            keyPassword = "abxdroppedapk"
        }
    }

    // extractNativeLibs=true
    packaging.jniLibs.useLegacyPackaging = true

    defaultConfig {
        applicationId = "com.example.abxoverflow.droppedapk.system"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
            signingConfig = signingConfigs.getByName("sdkLibrarySigningConfig")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("sdkLibrarySigningConfig")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.ezxhelper.core)
    implementation(libs.androidx.preferences)

    implementation(project(":library"))

    implementation(libs.refine.runtime)
    compileOnly(project(":droppedapk:hidden-api"))

}
