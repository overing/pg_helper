package org.cyc.pg_helper;

import java.util.HashMap;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.cyc.pg_helper.databinding.ViewFloatingWindowBinding;

public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";

    private static final String ACTION_CLOSE = "org.cyc.pg_helper.FloatingWindowService.ACTION_CLOSE";

    private static final String CHANNEL_ID = "FloatingWindowServiceChannel";

    private static final int NOTIFICATION_ID = 1234;

    private static final HashMap<String, String> TypeUrls;

    private WindowManager mWindowManager;
    private ViewFloatingWindowBinding mBinding;
    private PopupMenu mMainMenu;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            if (ACTION_CLOSE.equals(intent.getAction())) {
                close();
            }
        }
    };

    static {
        HashMap<String, String> mapping = new HashMap<String, String>();
        mapping.put("普", "https://wiki.52poke.com/wiki/%E4%B8%80%E8%88%AC%EF%BC%88%E5%B1%9E%E6%80%A7%EF%BC%89");
        mapping.put("飛", "https://wiki.52poke.com/wiki/%E9%A3%9E%E8%A1%8C%EF%BC%88%E5%B1%9E%E6%80%A7%EF%BC%89");
        mapping.put("火", "https://wiki.52poke.com/wiki/%E7%81%AB%EF%BC%88%E5%B1%9E%E6%80%A7%EF%BC%89");
        mapping.put("超", "https://wiki.52poke.com/wiki/%E8%B6%85%E8%83%BD%E5%8A%9B%EF%BC%88%E5%B1%9E%E6%80%A7%EF%BC%89");
        mapping.put("水", "https://wiki.52poke.com/wiki/%E6%B0%B4%EF%BC%88%E5%B1%9E%E6%80%A7%EF%BC%89");
        // TODO: 補完其他?
        TypeUrls = mapping;
    }

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
        if (!PGHelperApp.from(this).tryCommitFloatingWindowServiceRunning()) {
            Log.w(TAG, "fw service duplication");
            stopSelf();
        }

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

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
        if (mBinding == null) {
            initUi();
        }

        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "PG Helper Channel",
            NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Floating Window Service")
                .setContentText("PG Helper is running")
                .setSmallIcon(R.mipmap.ic_qs_tile)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    private void initUi() {
        mBinding = ViewFloatingWindowBinding.inflate(LayoutInflater.from(getApplicationContext()));

        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        float scale = getResources().getDisplayMetrics().density;
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.x = 0;
        layoutParams.y = 0;
        layoutParams.width = (int) (64 * scale + .5f);
        layoutParams.height = layoutParams.width;

        mWindowManager.addView(mBinding.root, layoutParams);

        final RectF touchOffset = new RectF();
        final Handler handler = new Handler();

        mBinding.toggleButton.setOnCheckedChangeListener((v, checked) -> {
            handler.removeCallbacksAndMessages(null);
            if (checked) {
                mMainMenu.show();

                handler.postDelayed(() -> {
                    mBinding.toggleButton.setChecked(false);
                }, 6000);
            } else {
                mMainMenu.dismiss();
            }
        });
        mBinding.toggleButton.setOnTouchListener((v, e) -> {
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

        mMainMenu = new PopupMenu(this, mBinding.root);
        mMainMenu.getMenuInflater().inflate(R.menu.menu_main, mMainMenu.getMenu());
        int index = 0;
        SubMenu typeMenu = mMainMenu.getMenu().addSubMenu(Menu.NONE, Menu.FIRST + (++index), Menu.NONE, "屬性");
        for (Map.Entry<String, String> entry : TypeUrls.entrySet()) {
            String title = entry.getKey();
            String url = entry.getValue();
            MenuItem item = typeMenu.add(Menu.NONE, Menu.FIRST + (++index), index, title);
            item.setOnMenuItemClickListener(i -> {
                viewUrl(url);
                return true;
            });
        }

        mMainMenu.setOnDismissListener(m -> {
            mBinding.toggleButton.setChecked(false);
        });
        mMainMenu.setOnMenuItemClickListener(i -> {
            switch (i.getItemId()) {
                case R.id.calculate_package_discounts:
                    viewUrl("https://docs.google.com/spreadsheets/d/1erLLHLkWAIfTYULB3jQrOeXSp322jNx7_xyEkBXVN9U/edit?usp=sharing");
                    return true;

                default:
                    return false;
            }
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

    private void viewUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        // TODO: 沒法從 service 當中主動使其他 app 進入 multi-window 或 picture-in-picture 模式
        // 可能要嘗試看看自己開一個 activity 內鑲 web view 的方式來做
    }

    private void close() {
        stopSelf();
        mWindowManager.removeView(mBinding.root);
    }
}