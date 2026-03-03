plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "me.timschneeberger.droppedapk.hiddenapi"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 23
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.annotation.jvm)
    annotationProcessor(libs.refine.annotation.processor)
    compileOnly(libs.refine.annotation)
}