package org.cyc.pg_helper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.net.HttpURLConnection;
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
        mainMenu.add(Menu.NONE, ++index, Menu.NONE, R.string.btn_type_table).setOnMenuItemClickListener(v -> {
            toggleViewPage(mWindowBinding, mWindowBinding.typeListPage.root);
            return true;
        });
        mainMenu.add(Menu.NONE, ++index, Menu.NONE, R.string.btn_gift_package_discount).setOnMenuItemClickListener(v -> {
            toggleViewPage(mWindowBinding, mWindowBinding.packageDiscountPage.root);
            return true;
        });
        mainMenu.add(Menu.NONE, ++index, Menu.NONE, R.string.btn_close).setOnMenuItemClickListener(v -> {
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

            LinkedHashMap<String, String> alias = new LinkedHashMap<String, String>();
            reader.beginObject();
            if (reader.nextName().equals("alias")) {
                reader.beginObject();
                while (reader.hasNext()) {
                    alias.put(reader.nextName(), reader.nextString());
                }
                reader.endObject();
            }

            if (reader.nextName().equals("table")) {
                reader.beginArray();
                for (int y = 0; reader.hasNext(); y++) {
                    TableRow tableRow = new TableRow(viewContext);
                    tableLayout.addView(tableRow);

                    reader.beginArray();
                    for (int x = 0; reader.hasNext(); x++) {
                        String value = reader.nextString();
                        value = alias.getOrDefault(value, value);

                        TableRow.LayoutParams cellLayoutParams = new TableRow.LayoutParams();
                        cellLayoutParams.width = TableRow.LayoutParams.MATCH_PARENT;
                        cellLayoutParams.height = TableRow.LayoutParams.MATCH_PARENT;
                        cellLayoutParams.weight = 1;
                        cellLayoutParams.setMargins(0, 0, (x < 18) ? -1 : 0, (y < 18) ? -1 : 0); // 讓框線完整重疊

                        TextView textView = new TextView(viewContext);
                        textView.setGravity(Gravity.CENTER);
                        textView.setText(value);
                        textView.setPadding(2, -4, 2, 0);
                        textView.setBackgroundResource(R.drawable.border_gray);
                        tableRow.addView(textView, cellLayoutParams);

                        final int cellX = x;
                        final int cellY = y;
                        textView.setOnClickListener(v -> onClickTypeTableCell(v, cellX, cellY));
                    }
                    reader.endArray();
                }
                reader.endArray();
            }
            reader.endObject();

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

        ArrayList<GiftPackageItem> items;
        try {
            try {
                items = readItemsFromWeb();
            } catch (Exception ex) {
                Toast.makeText(this, "讀取網路價格表發生錯誤: " + ex.getMessage(), Toast.LENGTH_LONG);
                Log.e(TAG, "Read web item price table fault", ex);

                items = readItemsFromRes();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "讀取價格表發生錯誤: " + ex.getMessage(), Toast.LENGTH_LONG);
            Log.e(TAG, "Read item price table fault", ex);
            return;
        }

        int viewIndex = 1;
        for (GiftPackageItem item : items) {
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
    }

    ArrayList<GiftPackageItem> readItemsFromRes() throws IOException {
        InputStream input = getResources().openRawResource(R.raw.item_price);
        ArrayList<GiftPackageItem> items = readItemsFromStream(input);
        try { input.close(); } catch (Exception ignore) { }
        return items;
    }

    static ArrayList<GiftPackageItem> readItemsFromWeb() throws IOException {
        URL url = new URL("https://overing.github.io/pg_helper/app/src/main/res/raw/item_price.json");
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setRequestProperty("Cache-Control", "no-cache");
        urlConnection.setDefaultUseCaches(false);
        urlConnection.setUseCaches(false);
        urlConnection.connect();

        InputStream input = urlConnection.getInputStream();
        ArrayList<GiftPackageItem> items = readItemsFromStream(input);

        try { input.close(); } catch (Exception ignore) { }
        urlConnection.disconnect();
        return items;
    }

    static ArrayList<GiftPackageItem> readItemsFromStream(InputStream stream) throws IOException {
        ArrayList<GiftPackageItem> items = new ArrayList<GiftPackageItem>();

        JsonReader reader = new JsonReader(new InputStreamReader(stream, "UTF-8"));
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            int price = reader.nextInt();

            GiftPackageItem item = new GiftPackageItem(name, price);
            items.add(item);
        }
        reader.endObject();
        try { reader.close(); } catch (Exception ignore) { }

        return items;
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