package org.cyc.pg_helper;

import android.app.ActivityManager;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class MyQSTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        if (Settings.canDrawOverlays(this)) {
            startService(new Intent(getApplicationContext(), FloatingWindowService.class));
            Toast.makeText(getApplicationContext(), "Start Service", Toast.LENGTH_SHORT).show();

            updateTile();
        } else {
            Toast.makeText(getApplicationContext(), "Please Allow Display Floating Window", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }
    }

    private void updateTile() {
        // FIXME: not working... :(
        String serviceClassName = FloatingWindowService.class.getClass().getName();
        ActivityManager activityManager = (ActivityManager)getApplicationContext().getSystemService(ACTIVITY_SERVICE);
        ActivityManager.RunningServiceInfo exists = activityManager.getRunningServices(Integer.MAX_VALUE).stream()
            .filter(info -> info.service.getClassName().equals(serviceClassName))
            .findFirst()
            .orElse(null);

        Tile tile = getQsTile();
        if (exists == null) {
            tile.setState(Tile.STATE_INACTIVE);
        } else {
            tile.setState(Tile.STATE_ACTIVE);
        }
        tile.updateTile();
    }
}
