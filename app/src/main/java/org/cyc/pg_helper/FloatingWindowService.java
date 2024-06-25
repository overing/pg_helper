package org.cyc.pg_helper;

import java.util.ArrayList;
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
import android.os.Looper;
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
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.cyc.pg_helper.databinding.ViewFloatingWindowBinding;
import org.cyc.pg_helper.databinding.ViewGiftPackageDiscountPageBinding;
import org.cyc.pg_helper.databinding.ViewGiftPackageItemBinding;

public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";

    private static final String ACTION_CLOSE = "org.cyc.pg_helper.FloatingWindowService.ACTION_CLOSE";

    private static final String CHANNEL_ID = "FloatingWindowServiceChannel";

    private static final int NOTIFICATION_ID = 1234;

    private static final LinkedHashMap<String, String> TypeWeakStrong;

    private static final ArrayList<GiftPackageItem> GiftPackageItems;

    private WindowManager mWindowManager;
    private ViewFloatingWindowBinding mWindowBinding;
    private WindowManager.LayoutParams mWindowLayoutParams;

    private GiftPackageDiscount mGiftPackageDiscount = new GiftPackageDiscount();

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
        TypeWeakStrong = mapping;

        ArrayList<GiftPackageItem> items = new ArrayList<GiftPackageItem>();
        items.add(new GiftPackageItem("遠券", 175));
        items.add(new GiftPackageItem("特券", 83));
        items.add(new GiftPackageItem("孵蛋", 150));
        items.add(new GiftPackageItem("快孵", 200));
        items.add(new GiftPackageItem("滿藥", 20));
        items.add(new GiftPackageItem("復活", 30));
        items.add(new GiftPackageItem("薰香", 31));
        items.add(new GiftPackageItem("星碎", 80));
        items.add(new GiftPackageItem("運蛋", 63));
        GiftPackageItems = items;
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
        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
        mWindowBinding = ViewFloatingWindowBinding.inflate(inflater);
        mWindowLayoutParams = buildLayoutParams(64, 64);
        mWindowManager.addView(mWindowBinding.root, mWindowLayoutParams);

        ViewFloatingWindowBinding binding = mWindowBinding;

        closeAllPages();

        final RectF touchOffset = new RectF();
        final Handler handler = new Handler(Looper.getMainLooper());
        final PopupMenu popupMenu = new PopupMenu(this, binding.toggleButton);
        binding.toggleButton.setOnCheckedChangeListener((v, checked) -> {
            handler.removeCallbacksAndMessages(null);

            if (checked) {
                closeAllPages();

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

        initMenu(popupMenu.getMenu());

        initPackageDiscountPage(binding.packageDiscountPage, inflater);

        popupMenu.setOnDismissListener(m -> {
            mWindowBinding.toggleButton.setChecked(false);
        });
    }

    private void initMenu(Menu mainMenu) {
        int index = 0;
        SubMenu typeMenu = mainMenu.addSubMenu(Menu.NONE, Menu.FIRST + (++index), Menu.NONE, "屬性 弱勢　　　/　　　強勢");
        for (Map.Entry<String, String> entry : TypeWeakStrong.entrySet()) {
            String title = entry.getKey();
            String meta = entry.getValue();
            typeMenu.add(Menu.NONE, Menu.FIRST + (++index), index, title + "　 " + meta);
        }
        mainMenu.add(Menu.NONE, Menu.FIRST + (++index), Menu.NONE, "禮包折扣計算").setOnMenuItemClickListener(i -> onMenuItemClick_PackageCost(i));
        mainMenu.add(Menu.NONE, Menu.FIRST + (++index), Menu.NONE, "關閉").setOnMenuItemClickListener(i -> {
            close();
            return true;
        });
    }

    private void initPackageDiscountPage(ViewGiftPackageDiscountPageBinding packageDiscountPage, LayoutInflater inflater) {
        packageDiscountPage.setDiscount(mGiftPackageDiscount);

        packageDiscountPage.packageCostResetButton.setOnClickListener(v -> {
            for (GiftPackageItem item : GiftPackageItems) {
                item.setAmount(0);
            }
            mGiftPackageDiscount.setTotalPrice(0);
            mGiftPackageDiscount.setRealPrice(0);
        });

        packageDiscountPage.packageCostCloseButton.setOnClickListener(v -> closeAllPages());

        packageDiscountPage.realPrice.setOnClickListener(v -> {
            mGiftPackageDiscount.setRealPrice(0);
        });

        packageDiscountPage.action1.setText("+1");
        packageDiscountPage.action1.setOnClickListener(v -> {
            mGiftPackageDiscount.setRealPrice(mGiftPackageDiscount.getRealPrice() + 1);
        });

        packageDiscountPage.action2.setText("0");
        packageDiscountPage.action2.setOnClickListener(v -> {
            mGiftPackageDiscount.setRealPrice(mGiftPackageDiscount.getRealPrice() * 10);
        });

        int index = 0;
        for (GiftPackageItem item : GiftPackageItems) {
            ViewGiftPackageItemBinding itemBinding = ViewGiftPackageItemBinding.inflate(inflater);
            itemBinding.setItem(item);

            itemBinding.itemAmount.setOnClickListener(v -> {
                item.setAmount(0);
                calcGiftPackageTotalPrice();
            });

            itemBinding.itemAction1.setText("+1");
            itemBinding.itemAction1.setOnClickListener(v -> {
                item.setAmount(item.getAmount() + 1);
                calcGiftPackageTotalPrice();
            });

            itemBinding.itemAction2.setText("0");
            itemBinding.itemAction2.setOnClickListener(v -> {
                item.setAmount(item.getAmount() * 10);
                calcGiftPackageTotalPrice();
            });

            packageDiscountPage.root.addView(itemBinding.root, ++index);
        }
    }

    private boolean onMenuItemClick_PackageCost(MenuItem v) {
        ViewFloatingWindowBinding binding = mWindowBinding;
        if (binding.packageDiscountPage.root.getVisibility() == View.VISIBLE) {
            closeAllPages();
        } else {
            float scale = getResources().getDisplayMetrics().density;
            mWindowLayoutParams.width = (int) (280 * scale + .5f);
            mWindowLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            mWindowManager.updateViewLayout(binding.root, mWindowLayoutParams);

            binding.packageDiscountPage.root.setVisibility(View.VISIBLE);
            binding.root.requestLayout();
        }
        return true;
    }

    private void calcGiftPackageTotalPrice() {
        int total = 0;
        for (GiftPackageItem item : GiftPackageItems) {
            total += item.getTotal();
        }
        mGiftPackageDiscount.setTotalPrice(total);
    }

    private void closeAllPages() {
        Log.d(TAG, "closeAllPages");
        mWindowBinding.packageDiscountPage.root.setVisibility(View.GONE);

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