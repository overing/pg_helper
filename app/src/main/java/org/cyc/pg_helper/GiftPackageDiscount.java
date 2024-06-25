package org.cyc.pg_helper;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

public class GiftPackageDiscount extends BaseObservable {
    private int mTotalPrice;
    private int mRealPrice;

    @Bindable
    public int getTotalPrice() { return mTotalPrice; }

    @Bindable
    public int getRealPrice() { return mRealPrice; }

    @Bindable
    public float getDiscountRate() { return mTotalPrice > 0 ? (mRealPrice / (float)mTotalPrice) * 100 : 0; }

    @Bindable
    public String getRateFormat() { return "%.2f"; }

    public void setTotalPrice(int totalPrice) {
        mTotalPrice = totalPrice;
        notifyPropertyChanged(BR.totalPrice);
        notifyPropertyChanged(BR.discountRate);
    }

    public void setRealPrice(int realPrice) {
        mRealPrice = realPrice;
        notifyPropertyChanged(BR.realPrice);
        notifyPropertyChanged(BR.discountRate);
    }
}