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
        fun addShared(name: String, defaultProcess: String? = null, default: Boolean = false) {
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
                manifestPlaceholders["IS_SHELL"] = if (name == "android.uid.shell") "true" else "false"
                manifestPlaceholders["DEFAULT_PROCESS"] = defaultProcess
                    ?: if (name.contains(".uid.")) {
                        // For other Android system UIDs, you need to determine an existing process name that runs under that shared UID.
                        // Use the package name for a system app that runs under that shared UID in that case.
                        project.logger.warn("Shared UID '$name' has no known default process assigned")
                        name
                    } else name

                manifestPlaceholders["SHIZUKU_PROCESS"] = if (name == "android.uid.system") "com.android.settings"
                else "@null"

            }
        }

        // Android system shared UIDs
        addShared("android.uid.system", "system", true) // 1000
        addShared("android.uid.phone", "com.android.phone") // 1001
        addShared("android.uid.bluetooth", "com.android.bluetooth") // 1002
        addShared("android.uid.nfc", "com.android.nfc") // 1027
        addShared("android.uid.se", "com.android.se") // 1068
        addShared("android.uid.networkstack", "com.google.android.networkstack") // 1073
        addShared("android.uid.uwb", "com.samsung.android.uwb") // 1083
        addShared("android.uid.shell", "com.android.shell") // 2000
        // Vendor shared UIDs (Samsung)
        addShared("android.uid.sendhelpmessage", "com.sec.android.app.safetyassurance") // 5003
        addShared("android.uid.cmhservice", "com.samsung.cmh") // 5004
        addShared("android.uid.bcmgr", "com.samsung.android.beaconmanager") // 5006
        addShared("android.uid.samsungcloud", "com.samsung.android.scloud") // 5009
        addShared("android.uid.intelligenceservice", "com.samsung.android.rubinapp") // 5010 & 10057
        addShared("android.uid.nsflp", "com.sec.location.nsflp2") // 5013
        addShared("android.uid.advmodem", "com.samsung.android.samsungpositioning") // 5017
        addShared("android.uid.ipsgeofence", "com.samsung.android.ipsgeofence") // 5022
        addShared("android.uid.networkdiagnostic", "com.samsung.android.networkdiagnostic") // 5023
        addShared("android.uid.mdxkit", "com.samsung.android.mdx.kit") // 5025
        addShared("android.uid.sharelive", "com.samsung.android.app.sharelive") // 5026
        addShared("android.uid.knoxcore", "com.samsung.android.knox.containercore") // 5250
        addShared("android.uid.spass", "com.samsung.android.authfw") // 5278
        addShared("android.uid.spay", "com.samsung.android.spayfw") // 5279
        // User shared UIDs (10000+)
        addShared("android.uid.systemui", "com.android.systemui") // 10048
        addShared("android.uid.shared", "com.android.providers.userdictionary") // 10057
        addShared("com.sec.android.mimage.avatarstickers", "com.sec.android.mimage.avatarstickers") // 10063
        addShared("android.media", "com.android.mtp") // 10068
        addShared("com.samsung.android.app.cameraspecialshootingmodeviewer", "com.samsung.android.app.dofviewer") // 10071
        addShared("com.sec.android.mimage.uid.photoretouching", "com.sec.android.mimage.photoretouching") // 10109
        addShared("com.samsung.svoice", "com.samsung.android.svoiceime") // 10113
        addShared("android.uid.calendar", "com.android.providers.calendar") // 10114
        addShared("com.samsung.android.uid.dialer", "com.samsung.android.dialer") // 10122
        addShared("com.samsung.android.uid.video", "com.samsung.android.video") // 10132
        addShared("android.uid.smds", "com.samsung.android.sm.devicesecurity") // 10141
        addShared("com.samsung.android.marvin.feedback", "com.samsung.android.accessibility.talkback") // 10152
        addShared("android.uid.ve", "com.sec.android.app.vebgm") // 10161
        addShared("android.uid.asf_awareshare", "com.samsung.android.aware.service") // 10200
        addShared("android.uid.asf_mediashare", "com.samsung.android.allshare.service.mediashare") // 10202
        addShared("android.uid.honeyboard", "com.samsung.android.honeyboard") // 10254
        // WARNING: This completely breaks GMS: addShared("com.google.uid.shared") // 10268
        addShared("com.google.android.calendar.uid.shared", "com.google.android.calendar") // 10282
        addShared("com.android.cts.ctsshim", "com.android.cts.ctsshim") // 10312
        addShared("com.samsung.accessory.wmanager", "com.samsung.android.app.watchmanager") // 10327
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
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

    compileOnly(files("libs/services-dex2jar.jar"))
}
