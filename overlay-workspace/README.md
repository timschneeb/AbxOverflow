## Runtime resource overlays

This folder contains custom RROs that can be injected into a running system.

## Injection process

To create overlays with complex resources that can't be used in fabricated overlays, we need to make regular RROs.

Android verifies overlay signatures on every boot, before we can hook into the system_server. Even if we inject our overlay into the package cache, it would be reset and removed after every boot. On Samsung devices however, there is an additional check that allows certain overlays to be verified under certain circumstances, even with mismatching signatures. That would allow us to inject RROs permanently and persist across reboots.

### Method
    
To install (needs to be done after each boot)
* sign with the same certificate of com.example.abxoverflow.droppedapk.system
* set packageService -> this$0 -> mOverlayConfigSignaturePackage to com.example.abxoverflow.droppedapk.system
* install overlay
  
To enable (needs to be done after each boot):
* overlayService -> this$0 -> impl -> idmapManager -> mConfigSignaturePackage: set to com.networksecurity.resinject
* cmd overlay enable com.networksecurity.resinject

#### Override networkSecurityConfigRes

* packageService -> this$0 -> liveComputer -> mPackages -> {pkg} -> networkSecurityConfigRes

### Samsung-related notes

In assertOverlayIsValid use Samsung-specific check to circumvent signature checks persistingly over reboots.

    boolean z = true;
    if (androidPackage.getPackageName().startsWith("com.samsung.themedesigner")) {
    synchronized (this.mPm.mLock) {
        packageLPr4 = this.mPm.mSettings.getPackageLPr(androidPackage.getPackageName());
    }
    z = true ^ (packageLPr4 != null && SemSamsungThemeUtils.isValidThemeParkOverlay(androidPackage, packageLPr4.getLastUpdateTime()));
    Slog.i("PackageManager", "assertOverlayIsValid overlayPkgSetting " + packageLPr4 + " " + z);
    }
    
* isValidThemeParkOverlay(String pkg, long updateTime) -> checks for (empty) file at /data/overlays/themepark/{pkg}/{updateTime}. {pkg} directory must only contain the key file. Can be created with system_server in themecenter process.
* sign with the same certificate of com.example.abxoverflow.droppedapk.system
* initial install: set packageService -> this$0 -> mOverlayConfigSignaturePackage to com.example.abxoverflow.droppedapk.system
* create files at /data/overlays/themepark with lastUpdateTime as file name (see above) know that we know the lastUpdateTime.
    * read from packageService -> this$0 -> mSettings -> mPackages -> {pkg} -> lastUpdateTime
    * use lastUpdateTime value from abx system app to create files at /data/overlays/themepark
* needs to enabled manually, see AOSP method
    

