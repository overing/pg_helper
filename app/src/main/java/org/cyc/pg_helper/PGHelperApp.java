package org.cyc.pg_helper;

import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import android.util.Log;

public class PGHelperApp extends Application {

    private static final String TAG = "PGHelperApp";

    private AtomicBoolean mFloatingWindowServiceRunning = new AtomicBoolean(false);

    public static PGHelperApp from(Activity activity) {
        return (PGHelperApp) activity.getApplication();
    }

    public static PGHelperApp from(Service service) {
        return (PGHelperApp) service.getApplication();
    }

    public boolean tryCommitFloatingWindowServiceRunning() {
        return mFloatingWindowServiceRunning.compareAndSet(false, true);
    }

    public void setFloatingWindowServiceRunning(boolean value) {
        Log.d(TAG, "setFloatingWindowServiceRunning , " + value);
        mFloatingWindowServiceRunning.set(value);
    }

    public boolean isFloatingWindowServiceRunning() {
        boolean result = mFloatingWindowServiceRunning.get();
        Log.d(TAG, "isFloatingWindowServiceRunning , " + result);
        return result;
    }

    public boolean ensureFloatingWindow() {
        if (Settings.canDrawOverlays(this)) {
            if (!mFloatingWindowServiceRunning.get()) {
                startService(new Intent(getApplicationContext(), FloatingWindowService.class));
            }
            return true;
        }

        hintAllowDrawOverlay();
        return false;
    }

    private void hintAllowDrawOverlay() {
        Toast.makeText(getApplicationContext(), "Please Allow Display Floating Window", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }
}
