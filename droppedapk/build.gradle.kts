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
        minSdk = 31
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("com.github.yinyinnie.stetho:stetho:1.0.0")
    implementation("com.github.yinyinnie.stetho:stetho-js-rhino:1.0.0")
    implementation(project(":library"))

    implementation(libs.refine.runtime)
    compileOnly(project(":droppedapk:hidden-api"))
}

