## Kernel parameters
* sysfs/procfs
  * /sys/class/backlight/panel/brightness can be overclocked (dangerous in long-term?)
  * /sys/class/power_supply/battery/batt_misc_event; override wireless charging pad authentication for high speed charging
  * /sys/devices/system/cpu/cpu0/cpufreq/scaling_{min|max}_freq
  * /sys/kernel/gpu/gpu_{min_clock|max_clock|governor}
  * /sys/kernel/ems/emstune/req_mode
      >>   normal mode (idx=0)
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
 
    /sys/devices/system/cpu/cpu1/online
    /sys/devices/system/cpu/cpu2/online
    /sys/devices/system/cpu/cpu3/online
    /sys/devices/system/cpu/cpu4/online
    /sys/devices/system/cpu/cpu5/online
    /sys/devices/system/cpu/cpu6/online
    /sys/devices/system/cpu/cpu7/online
    /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
    /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
    /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq
    /sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq
    /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq
    /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq
  
    ### EMS interfaces
    /sys/kernel/ems/emstune/req_mode
    /sys/kernel/ems/emstune/req_cam_sub_mode
    /sys/kernel/ems/emstune/req_gsdk_sub_mode
    /sys/kernel/ems/emstune/req_mode_level
    /sys/kernel/ems/emstune/aio_tuner
    /sys/kernel/ems/ecs/req_cpus

## Debuggable build override
* Override signatures: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/util/apk/ApkSignatureVerifier.java;l=101;drc=61197364367c9e404c7da6900658f1b16c42d0da
https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/local/PackageManagerLocalImpl.java;l=100;drc=61197364367c9e404c7da6900658f1b16c42d0da

* Attach JVMTI agents (handleAttachStartupAgents/attemptAttachAgent): https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/app/ActivityThread.java;l=4833;drc=61197364367c9e404c7da6900658f1b16c42d0da;bpv=1;bpt=1?q=IApplicationThread
      * → Create small JVMTI agent that starts frida-gadget
* ActivityManagerService: detect application attach to attach agent
      * Proxy ActivityManagerService and override attachApplication to detect when apps are launched

## Other ideas
* Add Android ID edit
* Add Device config edits 
* intercept service binders: https://github.com/Chainfire/injectvm-binderjack
* https://github.com/yuanyang1991/systemserverhooker/blob/master/lib/src/main/java/com/yuanyang/lib/ServiceBinderInterceptor.java 
  * ContentService tracing/inject → override system settings for certain apps
* I managed to subclass hidden API classes -> check for useful applications in system_server

## app_process wrapping/injection
    // If debuggable flag is set, can add wrap.sh to native-lib dir to apply --invoke-with zygote wrapper
    // Could implement code injection and Xposed-like functionality here for apps (bootstrap frida-gadget?)
    // This is likely redundant with the above JVMTI agent approach
    https://developer.android.com/ndk/guides/wrap-script.html
    https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/com/android/internal/os/WrapperInit.java
