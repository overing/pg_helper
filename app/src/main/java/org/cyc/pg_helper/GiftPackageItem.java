package org.cyc.pg_helper;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

public class GiftPackageItem extends BaseObservable {
    private String mName;
    private int mUnit;
    private int mAmount;

    public GiftPackageItem(String name, int unit) {
        mName = name;
        mUnit = unit;
        mAmount = 0;
    }

    @Bindable
    public String getName() { return mName; }

    @Bindable
    public int getUnit() { return mUnit; }

    @Bindable
    public int getAmount() { return mAmount; }

    @Bindable
    public int getTotal() { return mAmount * mUnit; }

    public void setName(String name) {
        mName = name;
        notifyPropertyChanged(BR.name);
    }

    public void setUnit(int unit) {
        mUnit = unit;
        notifyPropertyChanged(BR.unit);
        notifyPropertyChanged(BR.total);
    }

    public void setAmount(int amount) {
        mAmount = amount;
        notifyPropertyChanged(BR.amount);
        notifyPropertyChanged(BR.total);
    }
}