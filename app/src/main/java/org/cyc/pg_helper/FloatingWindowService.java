package org.cyc.pg_helper;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.cyc.pg_helper.databinding.ViewFloatingWindowBinding;

public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";

    private static final String ACTION_CLOSE = "org.cyc.pg_helper.FloatingWindowService.ACTION_CLOSE";

    private WindowManager mWindowManager;
    private ViewFloatingWindowBinding mBinding;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            if (ACTION_CLOSE.equals(intent.getAction())) {
                close();
            }
        }
    };

    public static void sendCloseBroadcast(Context context) {
        Log.d(TAG, "sendCloseBroadcast");
        Intent intent = new Intent(ACTION_CLOSE);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (!PGHelperApp.from(this).tryCommitFloatingWindowServiceRunning()) {
            Log.w(TAG, "fw service duplication");
            stopSelf();
        }
        QuickSettingTileService.sendUpdateBroadcast(this);
        registerReceiver(mReceiver, new IntentFilter(ACTION_CLOSE), Context.RECEIVER_NOT_EXPORTED);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand , " + startId);
        int result = super.onStartCommand(intent, flags, startId);
        if (mBinding == null) {
            initUi();
        }
        return result;
    }

    private void initUi() {
        mBinding = ViewFloatingWindowBinding.inflate(LayoutInflater.from(getApplicationContext()));

        mBinding.closeConfirmView.setVisibility(View.GONE);

        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.CENTER;
        layoutParams.x = 0;
        layoutParams.y = 0;
        layoutParams.width = 512;
        layoutParams.height = 512;

        mWindowManager.addView(mBinding.root, layoutParams);

        final RectF touchOffset = new RectF();

        mBinding.button1.setOnClickListener(v -> {
            stopSelf();
            mWindowManager.removeView(mBinding.root);
            Intent backToHome = new Intent(getApplicationContext(), MainActivity.class);
            backToHome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(backToHome);
        });
        mBinding.closeButton.setOnClickListener(v -> {
            mBinding.mainView.setVisibility(View.GONE);
            mBinding.closeConfirmView.setVisibility(View.VISIBLE);
        });
        mBinding.sureCloseButton.setOnClickListener(v -> {
            close();
        });
        mBinding.cancelCloseButton.setOnClickListener(v -> {
            mBinding.closeConfirmView.setVisibility(View.GONE);
            mBinding.mainView.setVisibility(View.VISIBLE);
        });
        mBinding.title.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchOffset.set(layoutParams.x, layoutParams.y, e.getRawX(), e.getRawY());
                    break;

                case MotionEvent.ACTION_MOVE:
                    layoutParams.x = (int) (touchOffset.left - touchOffset.right + e.getRawX());
                    layoutParams.y = (int) (touchOffset.top - touchOffset.bottom + e.getRawY());
                    mWindowManager.updateViewLayout(mBinding.root, layoutParams);
                    break;
            }
            return false;
        });
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        PGHelperApp.from(this).setFloatingWindowServiceRunning(false);
        QuickSettingTileService.sendUpdateBroadcast(this);
        super.onDestroy();
    }

    private void close() {
        stopSelf();
        mWindowManager.removeView(mBinding.root);
    }
}