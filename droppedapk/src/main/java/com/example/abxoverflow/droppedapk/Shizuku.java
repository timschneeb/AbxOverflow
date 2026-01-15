package com.example.abxoverflow.droppedapk;

import static com.example.abxoverflow.droppedapk.Utils.printStream;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

public class Shizuku {
    private static final String TAG = "DroppedAPK_Shizuku";

    public static void launchAsSystem(Context ctx) {
        try {
            ApplicationInfo info = ctx.getPackageManager().getApplicationInfo("moe.shizuku.privileged.api", 0);
            // get directory from apk path
            String dir = info.sourceDir.substring(0, info.sourceDir.lastIndexOf('/'));

            java.lang.Process process =  Runtime.getRuntime().exec(dir + "/lib/arm64/libshizuku.so");
            printStream(TAG, process.getInputStream(), false);
            printStream(TAG, process.getErrorStream(), true);

            Toast.makeText(ctx, "Shizuku launched", Toast.LENGTH_SHORT).show();
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(ctx, "Shizuku is NOT installed", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Toast.makeText(ctx, "IOException while starting", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to start Shizuku", e);
        }
    }
}
