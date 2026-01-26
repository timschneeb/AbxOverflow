package com.example.abxoverflow.droppedapk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import com.example.abxoverflow.droppedapk.debug.ProcessActivityLauncher;
import com.example.abxoverflow.droppedapk.debug.ProcessLocator;

import java.util.Set;

public class SystemProcessTrampolineActivity extends Activity {

    private static final String TAG = "DroppedAPK";

    public static final String EXTRA_EXPLICIT_PROCESS = "com.example.abxoverflow.droppedapk.EXTRA_EXPLICIT_PROCESS";
    public static final String EXTRA_TARGET_INTENT = "com.example.abxoverflow.droppedapk.EXTRA_TARGET_INTENT";
    public static final String EXTRA_SELECT_PROCESS = "com.example.abxoverflow.droppedapk.EXTRA_SELECT_PROCESS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        @SuppressLint("UnsafeIntentLaunch")
        Intent intent = getIntent().getParcelableExtra(EXTRA_TARGET_INTENT);
        String explicitProcess = getIntent().getStringExtra(EXTRA_EXPLICIT_PROCESS);
        boolean selectProcess = getIntent().getBooleanExtra(EXTRA_SELECT_PROCESS, false);

        if (intent == null) {
            Toast.makeText(this, "No target intent provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            if (explicitProcess != null) {
                ProcessActivityLauncher.launch(this, intent, Process.myUid(), explicitProcess);
                finish();
            } else if (selectProcess) {
                selectProcessAndLaunch(intent);
                // Do not finish here, wait for user selection
            } else {
                startActivity(intent);
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Process-aware launch failed", e);
            Toast.makeText(this, "Error: " + e + " (" + e.getMessage() + ")", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void selectProcessAndLaunch(Intent intent) throws Exception {
        Set<String> proc = ProcessLocator.listActiveProcessNamesForUid(Process.myUid());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select process to switch to...");
        String[] processArray = proc.stream().sorted().toArray(String[]::new);
        builder.setItems(processArray, (dialog, which) -> {
            String selectedProcess = processArray[which];
            try {
                ProcessActivityLauncher.launch(
                        this,
                        intent,
                        Process.myUid(),
                        selectedProcess
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch activity in process " + selectedProcess, e);
                Toast.makeText(this, "Error: " + e + " (" + e.getMessage() + ")", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setOnDismissListener(dialog -> finish());
        builder.show();
    }
}
