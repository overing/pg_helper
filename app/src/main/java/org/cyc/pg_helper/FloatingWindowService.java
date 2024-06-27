package org.cyc.pg_helper;

import java.io.InputStream;
import java.io.InputStreamReader;
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
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.JsonReader;
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
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.cyc.pg_helper.databinding.ViewFloatingWindowBinding;
import org.cyc.pg_helper.databinding.ViewTypeListPageBinding;
import org.cyc.pg_helper.databinding.ViewGiftPackageDiscountPageBinding;
import org.cyc.pg_helper.databinding.ViewGiftPackageItemBinding;

public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";

    private static final String ACTION_CLOSE = "org.cyc.pg_helper.FloatingWindowService.ACTION_CLOSE";

    private static final String CHANNEL_ID = "FloatingWindowServiceChannel";

    private static final int NOTIFICATION_ID = 1234;

    private WindowManager mWindowManager;
    private ViewFloatingWindowBinding mWindowBinding;
    private WindowManager.LayoutParams mWindowLayoutParams;

    private GiftPackageDiscount mGiftPackageDiscount = new GiftPackageDiscount();

    private ArrayList<GiftPackageItem> mGiftPackageItems = new ArrayList<GiftPackageItem>();

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
        if (!PGHelperApp.from(this).tryCommitFloatingWindowServiceRunning()) {
            Log.w(TAG, "fw service duplication");
            stopSelf();
        }

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        QuickSettingTileService.sendUpdateBroadcast(this);
        registerReceiver(mReceiver, new IntentFilter(ACTION_CLOSE), RECEIVER_NOT_EXPORTED);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
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
        ViewFloatingWindowBinding binding = mWindowBinding = ViewFloatingWindowBinding.inflate(inflater);
        mWindowLayoutParams = buildLayoutParams();
        mWindowManager.addView(binding.root, mWindowLayoutParams);

        closeAllPages();

        PopupMenu popupMenu = new PopupMenu(this, binding.toggleButton);

        initToggle(popupMenu);

        initMenu(popupMenu.getMenu());

        initTypeListPage(binding.typeListPage, inflater);

        initPackageDiscountPage(binding.packageDiscountPage, inflater);

        popupMenu.setOnDismissListener(m -> {
            mWindowBinding.toggleButton.setChecked(false);
        });
    }

    private void initToggle(PopupMenu popupMenu) {
        ViewFloatingWindowBinding binding = mWindowBinding;
        final RectF touchOffset = new RectF();
        final Handler handler = new Handler(Looper.getMainLooper());
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
    }

    private void initMenu(Menu mainMenu) {
        int index = Menu.FIRST;
        mainMenu.add(Menu.NONE, ++index, Menu.NONE, "屬性克制關係").setOnMenuItemClickListener(v -> {
            toggleViewPage(mWindowBinding, mWindowBinding.typeListPage.root);
            return true;
        });
        mainMenu.add(Menu.NONE, ++index, Menu.NONE, "禮包折扣計算").setOnMenuItemClickListener(v -> {
            toggleViewPage(mWindowBinding, mWindowBinding.packageDiscountPage.root);
            return true;
        });
        mainMenu.add(Menu.NONE, ++index, Menu.NONE, "關閉").setOnMenuItemClickListener(v -> {
            close();
            return true;
        });
    }

    private void initTypeListPage(ViewTypeListPageBinding packageDiscountPage, LayoutInflater inflater) {
        JsonReader reader = null;
        try {
            InputStream input = getResources().openRawResource(R.raw.type_table);
            reader = new JsonReader(new InputStreamReader(input, "UTF-8"));

            TableLayout tableLayout = packageDiscountPage.root;
            Context viewContext = tableLayout.getContext();

            float scale = getResources().getDisplayMetrics().density;
            TableRow.LayoutParams cellLayoutParams = new TableRow.LayoutParams();
            cellLayoutParams.width = (int) (20 * scale + .5f);
            cellLayoutParams.height = (int) (20 * scale + .5f);
            cellLayoutParams.setMargins(0, 0, -1, -1);

            int cellY = 0;
            reader.beginArray();
            while (reader.hasNext()) {
                TableRow tableRow = new TableRow(viewContext);
                tableLayout.addView(tableRow);

                int cellX = 0;
                reader.beginArray();
                while (reader.hasNext()) {
                    String value = reader.nextString();
                    if (value.equals(("強"))) {
                        value = "●";
                    } else if (value.equals(("弱"))) {
                        value = "▼";
                    } else if (value.equals(("無"))) {
                        value = "✘";
                    }

                    TextView textView = new TextView(viewContext);
                    textView.setGravity(Gravity.CENTER);
                    textView.setText(value);
                    textView.setTextSize(12);
                    textView.setPadding(0, -2, 0, 0);
                    textView.setBackgroundResource(R.drawable.border_gray);
                    tableRow.addView(textView, cellLayoutParams);

                    final int x = cellX;
                    final int y = cellY;
                    textView.setOnClickListener(v -> onClickTypeTableCell(v, x, y));

                    ++cellX;
                }
                reader.endArray();

                ++cellY;
            }
            reader.endArray();

            tableLayout.requestLayout();
        } catch (Exception ex) {
            Toast.makeText(this, "讀取屬性表發生錯誤: " + ex.getMessage(), Toast.LENGTH_LONG);
            Log.e(TAG, "Read type table fault", ex);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignore) { }
            }
        }
    }

    private void initPackageDiscountPage(ViewGiftPackageDiscountPageBinding packageDiscountPage, LayoutInflater inflater) {
        packageDiscountPage.setDiscount(mGiftPackageDiscount);

        packageDiscountPage.packageCostResetButton.setOnClickListener(v -> {
            for (GiftPackageItem item : mGiftPackageItems) {
                item.setAmount(0);
            }
            mGiftPackageDiscount.setTotalPrice(0);
            mGiftPackageDiscount.setRealPrice(0);
        });

        packageDiscountPage.realPrice.setOnClickListener(v -> {
            mGiftPackageDiscount.setRealPrice(0);
        });

        packageDiscountPage.action1.setText("+1");
        packageDiscountPage.action1.setOnClickListener(v -> {
            mGiftPackageDiscount.setRealPrice(mGiftPackageDiscount.getRealPrice() + 1);
        });

        packageDiscountPage.action2.setText("+5");
        packageDiscountPage.action2.setOnClickListener(v -> {
            mGiftPackageDiscount.setRealPrice(mGiftPackageDiscount.getRealPrice() + 5);
        });

        packageDiscountPage.action3.setText("0");
        packageDiscountPage.action3.setOnClickListener(v -> {
            mGiftPackageDiscount.setRealPrice(mGiftPackageDiscount.getRealPrice() * 10);
        });

        int viewIndex = 1;
        JsonReader reader = null;
        try {
            InputStream input = getResources().openRawResource(R.raw.item_price);
            reader = new JsonReader(new InputStreamReader(input, "UTF-8"));

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                int price = reader.nextInt();

                GiftPackageItem item = new GiftPackageItem(name, price);
                mGiftPackageItems.add(item);

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

                itemBinding.itemAction2.setText("+5");
                itemBinding.itemAction2.setOnClickListener(v -> {
                    item.setAmount(item.getAmount() + 5);
                    calcGiftPackageTotalPrice();
                });

                itemBinding.itemAction3.setText("0");
                itemBinding.itemAction3.setOnClickListener(v -> {
                    item.setAmount(item.getAmount() * 10);
                    calcGiftPackageTotalPrice();
                });

                packageDiscountPage.root.addView(itemBinding.root, viewIndex++);
            }
            reader.endObject();
        } catch (Exception ex) {
            Toast.makeText(this, "讀取價格表發生錯誤: " + ex.getMessage(), Toast.LENGTH_LONG);
            Log.e(TAG, "Read item price table fault", ex);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignore) { }
            }
        }
    }

    private void onClickTypeTableCell(View v, int x, int y) {
        TableLayout tableLayout = mWindowBinding.typeListPage.root;

        for (int iy = 0; iy < tableLayout.getChildCount(); iy++) {
            TableRow tableRow = (TableRow) tableLayout.getChildAt(iy);
            for (int ix = 0; ix < tableRow.getVirtualChildCount(); ix++) {
                View view = tableRow.getVirtualChildAt(ix);

                int resId;
                if (x == 0 && y == 0) {
                    resId = R.drawable.border_gray;
                } else if (x == 0) {
                    resId = iy == y ? R.drawable.border_gray_selected : R.drawable.border_gray;
                } else if (y == 0) {
                    resId = ix == x ? R.drawable.border_gray_selected : R.drawable.border_gray;
                } else {
                    resId = (x == ix || y == iy) ? R.drawable.border_gray_selected : R.drawable.border_gray;
                }
                view.setBackgroundResource(resId);
            }
        }
    }

    private void calcGiftPackageTotalPrice() {
        int total = 0;
        for (GiftPackageItem item : mGiftPackageItems) {
            total += item.getTotal();
        }
        mGiftPackageDiscount.setTotalPrice(total);
    }

    private void toggleViewPage(ViewFloatingWindowBinding binding, View viewPage) {
        if (viewPage.getVisibility() == View.VISIBLE) {
            closeAllPages();
        } else {
            mWindowLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
            mWindowLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            mWindowManager.updateViewLayout(binding.root, mWindowLayoutParams);

            viewPage.setVisibility(View.VISIBLE);
            binding.root.requestLayout();
        }
    }

    private void closeAllPages() {
        mWindowBinding.typeListPage.root.setVisibility(View.GONE);
        mWindowBinding.packageDiscountPage.root.setVisibility(View.GONE);

        float scale = getResources().getDisplayMetrics().density;
        mWindowLayoutParams.width = (int) (64 * scale + .5f);
        mWindowLayoutParams.height = (int) (64 * scale + .5f);
        mWindowManager.updateViewLayout(mWindowBinding.root, mWindowLayoutParams);
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        int format = PixelFormat.TRANSLUCENT;
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(type, flags, format);

        SharedPreferences prefs = getSharedPreferences("_float_win", MODE_PRIVATE);

        float scale = getResources().getDisplayMetrics().density;
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.x = prefs.getInt("_win_pos_x", 0);
        layoutParams.y = prefs.getInt("_win_pos_y", 0);
        layoutParams.width = (int) (64 * scale + .5f);
        layoutParams.height = (int) (64 * scale + .5f);
        return layoutParams;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        PGHelperApp.from(this).setFloatingWindowServiceRunning(false);
        QuickSettingTileService.sendUpdateBroadcast(this);

        getSharedPreferences("_float_win", MODE_PRIVATE)
            .edit()
            .putInt("_win_pos_x", mWindowLayoutParams.x)
            .putInt("_win_pos_y", mWindowLayoutParams.y)
            .apply();

        super.onDestroy();
    }

    private void close() {
        stopSelf();
        mWindowManager.removeView(mWindowBinding.root);
    }
}