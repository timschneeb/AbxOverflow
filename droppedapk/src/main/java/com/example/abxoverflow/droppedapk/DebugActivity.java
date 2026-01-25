package com.example.abxoverflow.droppedapk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class DebugActivity extends Activity {
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        String id = "?";
        try {
            id = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("id").getInputStream())).readLine();
        } catch (IOException ignored) {}

        ((TextView) findViewById(R.id.app_text)).setText(
                "uid=" + Process.myUid() +
                "\npid=" + Process.myPid() +
                "\nprocess=" + ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? Process.myProcessName() : "?") +
                "\n\n" + id
        );
    }
}