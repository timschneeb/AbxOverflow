package com.example.abxoverflow.droppedapk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.os.ServiceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Map;

import me.timschneeberger.reflectionexplorer.Group;
import me.timschneeberger.reflectionexplorer.Instance;
import me.timschneeberger.reflectionexplorer.ReflectionExplorer;
import me.timschneeberger.reflectionexplorer.utils.dex.ParamNames;

public class MainActivity extends Activity {

    static {
        collectInstances();
    }

    private static final String TAG = "DroppedAPK";

    @SuppressLint("PrivateApi")
    private static void collectInstances() {
        ParamNames.INSTANCE.getAdditionalDexSearchPaths().add("/system/framework/framework.jar");

        Group serviceGroup = new Group("Accessible Services", null);
        Group inaccServiceGroup = new Group("Inaccessible Services", null);

        // Get all services
        try {
            for (String serviceName : ServiceManager.listServices()) {
                IBinder serviceObj = ServiceManager.getService(serviceName);

                if (serviceObj == null) {
                    Log.w(TAG, "Service " + serviceName + " is null, skipping");
                    continue;
                }

                if (serviceObj.getClass().getName().equals("android.os.BinderProxy")) {
                    ReflectionExplorer.INSTANCE.getInstances().add(new Instance(serviceObj, serviceName, inaccServiceGroup));
                    continue;
                }
                ReflectionExplorer.INSTANCE.getInstances().add(new Instance(serviceObj, serviceName, serviceGroup));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed listing services", e);
        }
    }
    private String printStream(InputStream stream, boolean isError) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
            if (isError) {
                Log.e(TAG, line);
            } else {
                Log.i(TAG, line);
            }
        }
        return output.toString();
    }

    private void showResultDialog(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO uncomment if frida required
        // System.loadLibrary("frida-gadget-android");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Mods.runAll();

        Button btnShell = this.findViewById(R.id.btn_shell);
        Button btnInspect = this.findViewById(R.id.btn_inspect);
        Button btnInternalDex = this.findViewById(R.id.btn_internal_dex);

        btnShell.setOnClickListener(v -> {
            final EditText input = new EditText(this);

            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Run command")
                    .setPositiveButton("Run", (dialog1, which) -> {
                        // Prevent dialog from closing automatically
                        try {
                            java.lang.Process process = Runtime.getRuntime().exec(input.getText().toString());

                            String out = printStream(process.getInputStream(), false);
                            String err = printStream(process.getErrorStream(), true);

                            input.setText("");

                            showResultDialog(out + "\n" + err);
                        } catch (IOException e) {
                            Toast.makeText(MainActivity.this, "IOException while starting", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Failed to start shell", e);

                            StringWriter sw = new StringWriter();
                            PrintWriter pw = new PrintWriter(sw);
                            e.printStackTrace(pw);
                            showResultDialog(sw.toString());
                        }
                    })
                    .setNegativeButton("Close", null)
                    .create();
            dialog.setView(input);
            dialog.show();
        });

        btnInspect.setOnClickListener(v -> ReflectionExplorer.INSTANCE.launchMainActivity(this));

        updateInternalDexButtonText(btnInternalDex);
        btnInternalDex.setOnClickListener(v -> {
            try {
                boolean enabled = Mods.getForcedInternalDexScreenModeEnabled();
                Mods.setForcedInternalDexScreenModeEnabled(!enabled);

                if (!enabled) {
                    // Turning on... give some time for DEX to initialize
                    btnInternalDex.setEnabled(false);
                    btnInternalDex.setText("Starting internal Samsung DEX screen...");
                    v.postDelayed(() -> {
                        btnInternalDex.setEnabled(true);
                        Mods.setDexExternalMouseConnected(true);
                        updateInternalDexButtonText(btnInternalDex);
                    }, 3000);
                } else {
                    // Turning off...
                    updateInternalDexButtonText(btnInternalDex);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to start/stop internal dex", e);
                Toast.makeText(MainActivity.this, "Failed to start/stop internal DEX screen: " + e + " (" + e.getMessage() + ")", Toast.LENGTH_SHORT).show();
            }
        });


        String id = "?";
        try {
            id = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("id").getInputStream())).readLine();
        } catch (IOException e) {}

        StringBuilder s = new StringBuilder();
        s
                .append(
                        "Note: Installation of this app involved registering new signature trusted for sharedUserId=android.uid.system," +
                                " if you uninstall usual way it will stay in system" +
                                " and you will be able to reinstall this app despite mismatched signature." +
                                " To fully uninstall use \"Uninstall\" button within this app" +
                                "\n\nuid=").append(Process.myUid())
                .append("\npid=").append(Process.myPid())
                .append("\n\n").append(id)
                .append("\n\nBelow is list of system services, as this app loads into system_server it can directly tamper with local ones (those that are non-null and non-BinderProxy)");

        try {
            for (String serviceName : ServiceManager.listServices()) {
                IBinder serviceObj = ServiceManager.getService(serviceName);
                s.append("\n\n").append(serviceName).append(":\n").append(
                        serviceObj != null ? serviceObj.toString() : "null"
                );
            }
        } catch (Exception e) {
            s.append("\n\nFailed listing services");
        }

        ((TextView) findViewById(R.id.app_text)).setText(s.toString());
    }

    private void updateInternalDexButtonText(Button btn) {
        try {
            boolean enabled = Mods.getForcedInternalDexScreenModeEnabled();
            int id = Mods.getDexDisplayId();
            btn.setText(enabled ? "Stop internal Samsung DEX screen (ID=" + id + ")" : "Start internal Samsung DEX screen");
        }
        catch (Exception e) {
            Log.e(TAG, "updateInternalDexButtonText: ", e);
            btn.setText("Internal Samsung DEX screen unsupported");
            btn.setEnabled(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.uninstall) {
            try {
                // Delete <pastSigs> by directly editing PackageManagerService state within system_server
                // ServiceManager.getService("package").this$0.mSettings.mSharedUsers.get("android.uid.system").getSigningDetails().mPastSigningCertificates = null
                Object packManImplService = ServiceManager.getService("package");
                Field packManImplThisField = packManImplService.getClass().getDeclaredField("this$0");
                packManImplThisField.setAccessible(true);
                Object packManService = packManImplThisField.get(packManImplService);
                Field settingsField = packManService.getClass().getDeclaredField("mSettings");
                settingsField.setAccessible(true);
                Object settings = settingsField.get(packManService);
                Field sharedUsersField = settings.getClass().getDeclaredField("mSharedUsers");
                sharedUsersField.setAccessible(true);
                Object sharedUser = ((Map) sharedUsersField.get(settings)).get("android.uid.system");
                Object signingDetails = sharedUser.getClass().getMethod("getSigningDetails").invoke(sharedUser);
                Field pastSigningCertificatesField = signingDetails.getClass().getDeclaredField("mPastSigningCertificates");
                pastSigningCertificatesField.setAccessible(true);
                pastSigningCertificatesField.set(signingDetails, null);

                // Uninstall this app (also triggers write of fixed packages.xml)
                getPackageManager().getPackageInstaller().uninstall(getPackageName(), null);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Uninstall failed", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}