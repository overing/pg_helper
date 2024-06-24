package org.cyc.pg_helper;

import java.util.LinkedHashMap;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.cyc.pg_helper.databinding.ViewFloatingWindowBinding;

public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";

    private static final String ACTION_CLOSE = "org.cyc.pg_helper.FloatingWindowService.ACTION_CLOSE";

    private static final String CHANNEL_ID = "FloatingWindowServiceChannel";

    private static final int NOTIFICATION_ID = 1234;

    private static final LinkedHashMap<String, String> TypeUrls;

    private WindowManager mWindowManager;
    private ViewFloatingWindowBinding mWindowBinding;
    private WindowManager.LayoutParams mWindowLayoutParams;

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
        LinkedHashMap<String, String> mapping = new LinkedHashMap<String, String>();
        mapping.put("普", "格");
        mapping.put("飛", "電冰岩　　/　　草格蟲");
        mapping.put("火", "水地岩　　/　草冰蟲鋼");
        mapping.put("超", "蟲幽惡　　/　　　格毒");
        mapping.put("水", "電草　　　/　　火地岩");
        mapping.put("蟲", "火飛岩　　/　　草超惡");
        mapping.put("電", "地　　　　/　　　水飛");
        mapping.put("岩", "水草格地鋼/　火冰飛蟲");
        mapping.put("草", "火冰毒飛蟲/　　水地岩");
        mapping.put("幽", "幽惡　　　/　　　超幽");
        mapping.put("冰", "火格岩鋼　/　草地飛龍");
        mapping.put("龍", "冰龍妖　　/　　　　龍");
        mapping.put("格", "飛超妖　　/普冰岩惡鋼");
        mapping.put("惡", "格蟲妖　　/　　　超幽");
        mapping.put("毒", "地超　　　/　　　草妖");
        mapping.put("鋼", "火格地　　/　　冰岩妖");
        mapping.put("地", "水草冰　　/火電毒岩鋼");
        mapping.put("妖", "毒鋼　　　/　　格龍惡");
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
        if (mWindowBinding == null) {
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
        mWindowBinding = ViewFloatingWindowBinding.inflate(LayoutInflater.from(getApplicationContext()));
        mWindowLayoutParams = buildLayoutParams(64, 64);
        mWindowManager.addView(mWindowBinding.root, mWindowLayoutParams);

        ViewFloatingWindowBinding binding = mWindowBinding;

        closePages();

        final RectF touchOffset = new RectF();
        final Handler handler = new Handler();
        final PopupMenu popupMenu = new PopupMenu(this, binding.toggleButton);
        binding.toggleButton.setOnCheckedChangeListener((v, checked) -> {
            handler.removeCallbacksAndMessages(null);

            if (checked) {
                closePages();

                popupMenu.show();

                handler.postDelayed(() -> mWindowBinding.toggleButton.setChecked(false), 6000);
            } else {
                popupMenu.dismiss();
            }
        });
        binding.toggleButton.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchOffset.set(mWindowLayoutParams.x, mWindowLayoutParams.y, e.getRawX(), e.getRawY());
                    break;

                case MotionEvent.ACTION_MOVE:
                    mWindowLayoutParams.x = (int) (touchOffset.left - touchOffset.right + e.getRawX());
                    mWindowLayoutParams.y = (int) (touchOffset.top - touchOffset.bottom + e.getRawY());
                    mWindowManager.updateViewLayout(mWindowBinding.root, mWindowLayoutParams);
                    break;
            }
            return false;
        });

        binding.packageCostResetButton.setOnClickListener(v -> {
            mWindowBinding.packageCostItem1Amount.setText("0");
            mWindowBinding.packageCostItem2Amount.setText("0");
            mWindowBinding.packageCostItem3Amount.setText("0");
            mWindowBinding.packageCostItem4Amount.setText("0");
            mWindowBinding.packageCostItem5Amount.setText("0");
            mWindowBinding.packageCostItem6Amount.setText("0");
            mWindowBinding.packageCostItem7Amount.setText("0");
            mWindowBinding.packageCostItem8Amount.setText("0");
            mWindowBinding.packageCostItem9Amount.setText("0");
            calcPackageCost();
        });

        binding.packageCostItem1Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem1Amount, 1));
        binding.packageCostItem2Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem2Amount, 1));
        binding.packageCostItem3Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem3Amount, 1));
        binding.packageCostItem4Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem4Amount, 1));
        binding.packageCostItem5Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem5Amount, 1));
        binding.packageCostItem6Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem6Amount, 1));
        binding.packageCostItem7Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem7Amount, 1));
        binding.packageCostItem8Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem8Amount, 1));
        binding.packageCostItem9Add1.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem9Amount, 1));

        binding.packageCostItem1Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem1Amount, 10));
        binding.packageCostItem2Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem2Amount, 10));
        binding.packageCostItem3Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem3Amount, 10));
        binding.packageCostItem4Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem4Amount, 10));
        binding.packageCostItem5Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem5Amount, 10));
        binding.packageCostItem6Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem6Amount, 10));
        binding.packageCostItem7Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem7Amount, 10));
        binding.packageCostItem8Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem8Amount, 10));
        binding.packageCostItem9Add10.setOnClickListener(v -> putPackageCostAmountText(binding.packageCostItem9Amount, 10));

        Menu mainMenu = popupMenu.getMenu();
        int index = 0;
        SubMenu typeMenu = mainMenu.addSubMenu(Menu.NONE, Menu.FIRST + (++index), Menu.NONE, "屬性 弱勢　　　/　　　強勢");
        for (Map.Entry<String, String> entry : TypeUrls.entrySet()) {
            String title = entry.getKey();
            String meta = entry.getValue();
            typeMenu.add(Menu.NONE, Menu.FIRST + (++index), index, title + "　 " + meta);
        }

        mainMenu.add(Menu.NONE, Menu.FIRST + (++index), Menu.NONE, "禮包折扣計算").setOnMenuItemClickListener(i -> onMenuItemClick_PackageCost(i));

        popupMenu.setOnDismissListener(m -> {
            mWindowBinding.toggleButton.setChecked(false);
        });
        // packageCostMenu.setOnMenuItemClickListener(i -> {
        //     switch (i.getItemId()) {
        //         case R.id.calculate_package_discounts:
        //             String url = "https://docs.google.com/spreadsheets/d/1erLLHLkWAIfTYULB3jQrOeXSp322jNx7_xyEkBXVN9U/edit?usp=sharing";
        //             InfoActivity.sendViewUrl(getApplicationContext(), url);
        //             return true;

        //         default:
        //             return false;
        //     }
        // });
    }

    private boolean onMenuItemClick_PackageCost(MenuItem v) {
        ViewFloatingWindowBinding binding = mWindowBinding;

        if (binding.packageCostPage.getVisibility() == View.VISIBLE) {
            closePages();
        } else {
            float scale = getResources().getDisplayMetrics().density;
            mWindowLayoutParams.width = (int) (320 * scale + .5f);
            mWindowLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            // .setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            mWindowManager.updateViewLayout(binding.root, mWindowLayoutParams);

            binding.pageFrame.setVisibility(View.VISIBLE);
            binding.packageCostPage.setVisibility(View.VISIBLE);
            binding.root.requestLayout();
        }
        return true;
    }

    private void putPackageCostAmountText(TextView textView, int add) {
        int amount = Integer.parseInt(textView.getText().toString());
        textView.setText(String.valueOf(amount + add));
        calcPackageCost();
    }

    private void calcPackageCost() {
        ViewFloatingWindowBinding binding = mWindowBinding;

        int item1unit = Integer.parseInt(binding.packageCostItem1Unit.getText().toString());
        int item2unit = Integer.parseInt(binding.packageCostItem2Unit.getText().toString());
        int item3unit = Integer.parseInt(binding.packageCostItem3Unit.getText().toString());
        int item4unit = Integer.parseInt(binding.packageCostItem4Unit.getText().toString());
        int item5unit = Integer.parseInt(binding.packageCostItem5Unit.getText().toString());
        int item6unit = Integer.parseInt(binding.packageCostItem6Unit.getText().toString());
        int item7unit = Integer.parseInt(binding.packageCostItem7Unit.getText().toString());
        int item8unit = Integer.parseInt(binding.packageCostItem8Unit.getText().toString());
        int item9unit = Integer.parseInt(binding.packageCostItem9Unit.getText().toString());

        int item1amount = Integer.parseInt(binding.packageCostItem1Amount.getText().toString());
        int item2amount = Integer.parseInt(binding.packageCostItem2Amount.getText().toString());
        int item3amount = Integer.parseInt(binding.packageCostItem3Amount.getText().toString());
        int item4amount = Integer.parseInt(binding.packageCostItem4Amount.getText().toString());
        int item5amount = Integer.parseInt(binding.packageCostItem5Amount.getText().toString());
        int item6amount = Integer.parseInt(binding.packageCostItem6Amount.getText().toString());
        int item7amount = Integer.parseInt(binding.packageCostItem7Amount.getText().toString());
        int item8amount = Integer.parseInt(binding.packageCostItem8Amount.getText().toString());
        int item9amount = Integer.parseInt(binding.packageCostItem9Amount.getText().toString());

        binding.packageCostItem1Total.setText(String.valueOf(item1unit * item1amount));
        binding.packageCostItem2Total.setText(String.valueOf(item2unit * item2amount));
        binding.packageCostItem3Total.setText(String.valueOf(item3unit * item3amount));
        binding.packageCostItem4Total.setText(String.valueOf(item4unit * item4amount));
        binding.packageCostItem5Total.setText(String.valueOf(item5unit * item5amount));
        binding.packageCostItem6Total.setText(String.valueOf(item6unit * item6amount));
        binding.packageCostItem7Total.setText(String.valueOf(item7unit * item7amount));
        binding.packageCostItem8Total.setText(String.valueOf(item8unit * item8amount));
        binding.packageCostItem9Total.setText(String.valueOf(item9unit * item9amount));
    }

    private void closePages() {
        Log.d(TAG, "closePages");
        mWindowBinding.pageFrame.setVisibility(View.GONE);
        mWindowBinding.packageCostPage.setVisibility(View.GONE);

        float scale = getResources().getDisplayMetrics().density;
        mWindowLayoutParams.width = (int) (64 * scale + .5f);
        mWindowLayoutParams.height = (int) (64 * scale + .5f);
        mWindowManager.updateViewLayout(mWindowBinding.root, mWindowLayoutParams);
    }

    private WindowManager.LayoutParams buildLayoutParams(int width, int height) {
        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        int format = PixelFormat.TRANSLUCENT;
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(type, flags, format);

        float scale = getResources().getDisplayMetrics().density;
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.x = 0;
        layoutParams.y = 0;
        layoutParams.width = (int) (width * scale + .5f);
        layoutParams.height = (int) (height * scale + .5f);
        return layoutParams;
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
        mWindowManager.removeView(mWindowBinding.root);
    }
}