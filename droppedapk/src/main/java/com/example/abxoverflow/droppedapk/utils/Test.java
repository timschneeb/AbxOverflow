package com.example.abxoverflow.droppedapk.utils;

import android.util.Log;

import com.android.server.LocalManagerRegistry;
import com.android.server.SystemConfig;

public class Test extends SystemConfig {
    public Test() {
    }

    public void test() {
        Log.e("DroppedAPK_Mods", "Test OK");
        this.mOverlayConfigSignaturePackage = "this was modified by DroppedAPK :)";
        Log.e("DroppedAPK_Mods", "mOverlayConfigSignaturePackage: " + this.mOverlayConfigSignaturePackage);
    }
}
