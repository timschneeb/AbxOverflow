Override PackageManagerService.mPlatformPackage and attach signatures to fake isPlatformSigned:
        
        public boolean isPlatformSigned(String packageName) {
            PackageStateInternal packageState = snapshot().getPackageStateInternal(packageName);
            if (packageState == null) {
                return false;
            }
            SigningDetails signingDetails = packageState.getSigningDetails();
            return signingDetails.hasAncestorOrSelf(mPlatformPackage.getSigningDetails())
                    || mPlatformPackage.getSigningDetails().checkCapability(signingDetails,
                    SigningDetails.CertCapabilities.PERMISSION);
        }

FLAG_DEBUGGABLE
FLAG_FULL_BACKUP_ONLY
FLAG_ALLOW_BACKUP


public boolean isAppBackupAllowed(ApplicationInfo app)

ProxyTransactListener or BinderInternal.Observer
https://cs.android.com/android/platform/superproject/+/android-latest-release:external/cronet/stable/base/android/java/src/org/chromium/base/BinderCallsListener.java?q=setProxyTransactListener&ss=android%2Fplatform%2Fsuperproject

BroadcastHistory

ActivityInterceptorCallback

* SettingsService (for internal settings read/write)