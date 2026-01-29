## Run as any user app
    // Enable run-as. Only modifies /data/system/packages.list.
    // Debuggable flags in /data/system/packages.xml are not updated by this! -> undetectable
    PackageService -> this -> mSettings -> mPackages[] -> pkg -> setDebuggable
    PackageService -> this -> writeSettings(sync=true)
    (see writePackageListLPrInternal)

## app_process wrapping/injection
    // If debuggable flag is set, can add wrap.sh to native-lib dir to apply --invoke-with zygote wrapper
    // Could implement code injection and Xposed-like functionality here for apps
    https://developer.android.com/ndk/guides/wrap-script.html
    https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/com/android/internal/os/WrapperInit.java

## Make any app debuggable
    // To set a package fully debuggable (detectable!):
    PackageService -> this -> mSettings -> mPackages[] -> setDebuggable AND publicFlags |= FLAG_DEBUGGABLE

## Circumvent PairIP / Play Store checks:
    PackageService -> this -> mSettings -> mPackages[] -> installSource
      -> mInstallerPackageName = com.android.vending

## Take over other shared_uids
* Add automatic package shared uid insertion (like enhanced AbxDropper)

## Trust user certificates
* Modify manifest to add android:networkSecurityConfig pointing to custom XML
* Needs resource overlay to add XML file to res/xml (check if possible)

## Other ideas
* /data/misc: https://cs.android.com/android/platform/superproject/+/android-latest-release:system/sepolicy/private/system_server.te;l=649?q=system_server
* https://cs.android.com/android/platform/superproject/+/android-latest-release:system/sepolicy/contexts/file_contexts_test_data?
* /data/server_configurable_flags & /data/server_configurable_flags/test
* com.android.providers.settings/databases/settings.db
* Possibly inject custom audio effects library?
* Disable built-in (always-on) effects?
  * Ref: https://github.com/Magisk-Modules-Alt-Repo/audio-misc-settings/blob/master/service.sh
* Disable storage restrictions?
  * Ref: https://github.com/Xposed-Modules-Repo/com.github.dan.nostoragerestrict/blob/main/app/src/main/java/com/github/dan/NoStorageRestrict/FolderRestrictionhookA14.java

## System stuff to hook into
* BroadcastHistory: monitor broadcasts
* ActivityInterceptorCallback: intercept activity launches
* SettingsService (for internal settings read/write)
* ProxyTransactListener or BinderInternal.Observer: client/server-side binder IPC monitoring
  https://cs.android.com/android/platform/superproject/+/android-latest-release:external/cronet/stable/base/android/java/src/org/chromium/base/BinderCallsListener.java?q=setProxyTransactListener&ss=android%2Fplatform%2Fsuperproject

## Kernel parameters
* sysfs/procfs
  * /sys/class/backlight/panel/brightness can be overclocked
  * /sys/class/power_supply/battery/batt_misc_event; override wireless charging pad authentication
  * /sys/class/sec/switch/: UART/USB stuff
  * /sys/devices/system/cpu/cpu0/cpufreq/scaling_{min|max}_freq
  * /sys/kernel/gpu/gpu_{min_clock|max_clock|governor}
  * /proc/fslog
  * /proc/avc_msg
  * /sys/kernel/ems/emstune/req_mode
  ```
      >> normal mode (idx=0)
      power-scenario mode (idx=1)
      performance mode (idx=2)
      game mode (idx=3)
      balance mode (idx=4)
      camera mode (idx=5)
      camera sub mode 1 mode (idx=6)
      camera sub mode 2 mode (idx=7)
      camera sub mode 3 mode (idx=8)
      camera sub mode 4 mode (idx=9)
      gameSDK mode (idx=10)
      gameSDK sub mode 1 mode (idx=11)
      gameSDK sub mode 2 mode (idx=12)
      gameSDK sub mode 3 mode (idx=13)
      gameSDK sub mode 4 mode (idx=14)
      browser mode (idx=15)
    ```
