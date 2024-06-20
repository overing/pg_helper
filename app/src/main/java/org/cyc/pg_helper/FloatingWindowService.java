package org.cyc.pg_helper;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import org.cyc.pg_helper.databinding.ViewFloatingWindowBinding;

public class FloatingWindowService extends Service {
    private static final String TAG = "FloatingWindowService";

    private ViewFloatingWindowBinding mBinding;

    private double mTouchDownViewX;
    private double mTouchDownViewY;
    private double mTouchDownX;
    private double mTouchDownY;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand , " + startId);
        if (mBinding == null) {
            Log.d(TAG, "onStartCommand: Create Floating Window");
            initUi();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void initUi() {
        final WindowManager windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        mBinding = ViewFloatingWindowBinding.inflate(LayoutInflater.from(getApplicationContext()));

        mBinding.closeConfirmView.setVisibility(View.GONE);

        DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.CENTER;
        layoutParams.x = 0;
        layoutParams.y = 0;
        layoutParams.width = (int)(metrics.widthPixels * .3f);
        layoutParams.height = layoutParams.width;

        windowManager.addView(mBinding.root, layoutParams);

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
                    mTouchDownViewX = layoutParams.x;
                    mTouchDownViewY = layoutParams.y;
                    mTouchDownX = e.getRawX();
                    mTouchDownY = e.getRawY();
                    break;
    
                case MotionEvent.ACTION_MOVE:
                    layoutParams.x = (int) ((mTouchDownViewX + e.getRawX()) - mTouchDownX);
                    layoutParams.y = (int) ((mTouchDownViewY + e.getRawY()) - mTouchDownY);
                    windowManager.updateViewLayout(mBinding.root, layoutParams);
                    break;
            }
            return false;
        });
    }
}