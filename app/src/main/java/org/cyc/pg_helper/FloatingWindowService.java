package org.cyc.pg_helper;

import android.app.Service;
import android.content.Intent;
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

    private ViewFloatingWindowBinding mBinding;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        if (!PGHelperApp.from(this).tryCommitFloatingWindowServiceRunning()) {
            Log.w(TAG, "fw service duplication");
            stopSelf();
        }
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
        if (mBinding == null) {
            initUi();
        }
        QuickSettingTileService.sendUpdateBroadcast(this);
        return super.onStartCommand(intent, flags, startId);
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

        final WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(mBinding.root, layoutParams);

        final RectF touchOffset = new RectF();

        mBinding.button1.setOnClickListener(v -> {
            stopSelf();
            windowManager.removeView(mBinding.root);
            Intent backToHome = new Intent(getApplicationContext(), MainActivity.class);
            backToHome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(backToHome);
        });
        mBinding.closeButton.setOnClickListener(v -> {
            mBinding.mainView.setVisibility(View.GONE);
            mBinding.closeConfirmView.setVisibility(View.VISIBLE);
        });
        mBinding.sureCloseButton.setOnClickListener(v -> {
            stopSelf();
            windowManager.removeView(mBinding.root);
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
                    layoutParams.x = (int) ((touchOffset.left + e.getRawX()) - touchOffset.right);
                    layoutParams.y = (int) ((touchOffset.top + e.getRawY()) - touchOffset.bottom);
                    windowManager.updateViewLayout(mBinding.root, layoutParams);
                    break;
            }
            return false;
        });
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        PGHelperApp.from(this).setFloatingWindowServiceRunning(false);
        QuickSettingTileService.sendUpdateBroadcast(this);
        super.onDestroy();
    }
}