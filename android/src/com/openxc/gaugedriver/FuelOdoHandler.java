package com.openxc.gaugedriver;

import java.util.ArrayList;
import java.util.List;

public class FuelOdoHandler {

    private long mDuration;  //This is the time over which we do our calculations.
    private List<Long> mTimes = new ArrayList<Long>();
    private List<Double> mValues = new ArrayList<Double>();

    FuelOdoHandler(long smoothTime) {  //smoothTime units defined in the calling function.
        mDuration = smoothTime;
    }

    public synchronized void add(double value, long thisTime) {
        mValues.add(value);
        mTimes.add(thisTime);
    }

    public synchronized double latest() {
        if (mValues.size() > 0) {
            return mValues.get(mValues.size()-1);
        } else {
            return 0;
        }
    }

    public synchronized double recalculate(long thisTime) {
        long expiration = thisTime - mDuration;
        while ((mTimes.size()>2) && (mTimes.get(0) < expiration)) {
            mTimes.remove(0);
            mValues.remove(0);
        }

        if(mValues.size() >= 2) {
            return mValues.get(mValues.size()-1) - mValues.get(0);
        } else {
            return 0;
        }
    }
}
