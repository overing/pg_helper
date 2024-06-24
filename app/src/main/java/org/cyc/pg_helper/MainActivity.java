package org.cyc.pg_helper;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;

import org.cyc.pg_helper.databinding.ActivityMainBinding;

public class MainActivity extends ComponentActivity {

    private static final String ACTION_CONFIG = "org.cyc.pg_helper.MainActivity.ACTION_CONFIG";

    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(LayoutInflater.from(getBaseContext()));
        setContentView(mBinding.root);

        PGHelperApp.from(this).ensureFloatingWindow();

        Intent intent = getIntent();
        if (intent == null || !ACTION_CONFIG.equals(intent.getAction())) {
            finish();
        }
    }

}
