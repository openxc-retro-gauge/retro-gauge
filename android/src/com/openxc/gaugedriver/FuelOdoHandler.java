package com.openxc.gaugedriver;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class FuelOdoHandler {
	
	long mDuration;  //This is the time over which we do our calculations.
	List<Long> mTimes = new ArrayList<Long>();
	List<Double> mValues = new ArrayList<Double>();
	
	FuelOdoHandler(long smoothTime) {  //smoothTime units defined in the calling function.
		mDuration = smoothTime;
	}
	
	void Add(double value, long thisTime) {
		mValues.add(value);
		mTimes.add(thisTime);
	}
	
	double Latest() {
		if (mValues.size() > 0)
			return mValues.get(mValues.size()-1);
		else
			return 0.0;
	}
	
	double Recalculate(long thisTime) {
		long expiration = thisTime - mDuration;
		while ((mTimes.size()>2) && (mTimes.get(0) < expiration)) {
			mTimes.remove(0);
			mValues.remove(0);
		}
		
		if(mValues.size() >=2)
			return mValues.get(mValues.size()-1) - mValues.get(0);
		else
			return 0.0;
	}
}
