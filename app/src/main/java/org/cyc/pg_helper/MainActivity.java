package org.cyc.pg_helper;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import org.cyc.pg_helper.databinding.ActivityMainBinding;

public class MainActivity extends ComponentActivity {

    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(LayoutInflater.from(getBaseContext()));
        setContentView(mBinding.root);

        if (Settings.canDrawOverlays(this)) {
            startService(new Intent(getApplicationContext(), FloatingWindowService.class));
            Toast.makeText(getApplicationContext(), "Start Service", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Please Allow Display Floating Window", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }
    }

}
