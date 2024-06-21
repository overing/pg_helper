package org.cyc.pg_helper;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.Nullable;

public class QuickSettingTileService extends TileService {

    private static final String TAG = "QuickSettingTileService";

    private static final String ACTION_UPDATE_TILE = "org.cyc.pg_helper.QuickSettingTileService.ACTION_UPDATE_TILE";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            if (ACTION_UPDATE_TILE.equals(intent.getAction())) {
                updateTile();
            }
        }
    };

    public static void sendUpdateBroadcast(Context context) {
        Log.d(TAG, "sendUpdateBroadcast");
        Intent intent = new Intent(ACTION_UPDATE_TILE);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        registerReceiver(mReceiver, new IntentFilter(ACTION_UPDATE_TILE), Context.RECEIVER_NOT_EXPORTED);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        IBinder result = super.onBind(intent);
        TileService.requestListeningState(this, new ComponentName(this, QuickSettingTileService.class.getName()));
        updateTile();
        return result;
    }

    @Override
    public void onStartListening() {
        Log.d(TAG, "onStartListening");
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        Log.d(TAG, "onClick");
        PGHelperApp app = PGHelperApp.from(this);
        if (app.isFloatingWindowServiceRunning()) {
            FloatingWindowService.sendCloseBroadcast(this);
        } else {
            app.ensureFloatingWindow();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void updateTile() {
        Log.d(TAG, "updateTile");
        Tile tile = getQsTile();
        if (PGHelperApp.from(this).isFloatingWindowServiceRunning()) {
            tile.setState(Tile.STATE_ACTIVE);
        } else {
            tile.setState(Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }

}
