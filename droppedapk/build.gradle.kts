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
        // base application id; product flavors append suffixes
        applicationId = "com.example.abxoverflow.droppedapk"
        minSdk = 29
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Derive provider authority from the final applicationId at manifest merge time
        manifestPlaceholders["PROVIDER_AUTHORITY"] = $$"${applicationId}.provider.PrivilegedDocumentsProvider"
    }

    // New: flavor dimension + product flavors for different shared UIDs
    flavorDimensions += "sharedUid"
    productFlavors {
        fun addShared(name: String, default: Boolean = false) {
            val simpleName = if (name.startsWith("android.uid."))
                name.removePrefix("android.uid.").replaceFirstChar(Char::uppercaseChar)
            else
                name

            create(simpleName.replace('.', '_')) {
                isDefault = default
                dimension = "sharedUid"
                applicationIdSuffix = ".${simpleName.replace('.','_').lowercase()}"
                manifestPlaceholders["SHARED_USER_ID"] = name
                manifestPlaceholders["APP_LABEL"] = simpleName
                manifestPlaceholders["HAS_SYSTEM_SERVER"] = if (name == "android.uid.system") "true" else "false"

                // TODO
                manifestPlaceholders["DEFAULT_PROCESS"] = if (name == "android.uid.systemui") "com.android.systemui"
                else if (name.startsWith("android.uid.")) name.removePrefix("android.uid.")
                else name

            }
        }

        // Android system shared UIDs
        addShared("android.uid.system", true) // 1000
        addShared("android.uid.phone") // 1001
        addShared("android.uid.bluetooth") // 1002
        addShared("android.uid.nfc") // 1027
        addShared("android.uid.se") // 1068
        addShared("android.uid.networkstack") // 1073
        addShared("android.uid.uwb") // 1083
        addShared("android.uid.shell") // 2000
        // Vendor shared UIDs (Samsung)
        addShared("android.uid.sendhelpmessage") // 5003
        addShared("android.uid.cmhservice") // 5004
        addShared("android.uid.bcmgr") // 5006
        addShared("android.uid.samsungcloud") // 5009
        addShared("android.uid.intelligenceservice") // 5010 & 10057
        addShared("android.uid.nsflp") // 5013
        addShared("android.uid.advmodem") // 5017
        addShared("android.uid.ipsgeofence") // 5022
        addShared("android.uid.networkdiagnostic") // 5023
        addShared("android.uid.mdxkit") // 5025
        addShared("android.uid.sharelive") // 5026
        addShared("android.uid.knoxcore") // 5250
        addShared("android.uid.spass") // 5278
        addShared("android.uid.spay") // 5279
        // User shared UIDs (10000+)
        addShared("android.uid.systemui") // 10048
        addShared("com.sec.android.mimage.avatarstickers") // 10063
        addShared("android.media") // 10068
        addShared("com.samsung.android.app.cameraspecialshootingmodeviewer") // 10071
        addShared("com.sec.android.mimage.uid.photoretouching") // 10109
        addShared("com.samsung.svoice") // 10113
        addShared("android.uid.calendar") // 10114
        addShared("com.samsung.android.uid.dialer") // 10122
        addShared("com.samsung.android.uid.video") // 10132
        addShared("android.uid.smds") // 10141
        addShared("com.samsung.android.marvin.feedback") // 10152
        addShared("android.uid.ve") // 10161
        addShared("android.uid.asf_awareshare") // 10200
        addShared("android.uid.asf_mediashare") // 10202
        addShared("android.uid.honeyboard") // 10254
        // WARNING: This completely breaks GMS: addShared("com.google.uid.shared") // 10268
        addShared("com.google.android.calendar.uid.shared") // 10282
        addShared("com.android.cts.ctsshim") // 10312
        addShared("com.samsung.accessory.wmanager") // 10327
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
