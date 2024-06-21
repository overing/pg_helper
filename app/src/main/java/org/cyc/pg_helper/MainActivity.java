package org.cyc.pg_helper;

import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;

import org.cyc.pg_helper.databinding.ActivityMainBinding;

public class MainActivity extends ComponentActivity {

    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(LayoutInflater.from(getBaseContext()));
        setContentView(mBinding.root);

        PGHelperApp.from(this).ensureFloatingWindow();
    }

}
