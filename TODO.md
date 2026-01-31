## app_process wrapping/injection
    // If debuggable flag is set, can add wrap.sh to native-lib dir to apply --invoke-with zygote wrapper
    // Could implement code injection and Xposed-like functionality here for apps
    https://developer.android.com/ndk/guides/wrap-script.html
    https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/com/android/internal/os/WrapperInit.java

## Trust user certificates
* Modify manifest to add android:networkSecurityConfig pointing to custom XML
* Needs resource overlay to add XML file to res/xml (check if possible)

## Feature flags
* https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/java/android/util/FeatureFlagUtils.java?q=case:y%20%22SystemProperties.get%22%20-ro%5C.%20file:.java%20-file:test%20-file:AdServices%20-file:Car%20-file:hiddenapi%20-file:cts&ss=android%2Fplatform%2Fsuperproject&start=101
* https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/packages/SystemUI/src/com/android/systemui/flags/Flags.kt

## Launch trampoline
* Apps without running processes (e.g com.android.shell) cannot be launched from the home screen.
* Add trampoline home activity

## Reflection Explorer
* Allow text input of class to get a list of static methods/fields
* Implement search algorithm for static methods/fields

## SystemUI
* Dumpables
* Tunables

## Other ideas
* JVMTI agent can be attached to system_server. Other apps need to be debuggable.
* selinux parser to find interesting Samsung OEM modifications from CIL
* PackageManagerServiceUtils
  * comparePackageSignatures: set PkgSetting.signingDetails to SigningDetails.UNKNOWN to skip.
* InstallPackageHelper
  * assertOverlayIsValid

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
  ```

## System stuff to hook into for monitoring (low priority)
* BroadcastHistory: monitor broadcasts
* ActivityInterceptorCallback: intercept activity launches
* SettingsService (for internal settings read/write)
* ProxyTransactListener or BinderInternal.Observer: client/server-side binder IPC monitoring
  https://cs.android.com/android/platform/superproject/+/android-latest-release:external/cronet/stable/base/android/java/src/org/chromium/base/BinderCallsListener.java?q=setProxyTransactListener&ss=android%2Fplatform%2Fsuperproject
