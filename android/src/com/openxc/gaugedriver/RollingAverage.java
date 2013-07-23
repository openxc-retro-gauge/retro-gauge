package com.openxc.gaugedriver;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;

public class RollingAverage {
    private final int DEFAULT_WINDOW_SIZE = 200;

    private int mWindowSize = DEFAULT_WINDOW_SIZE;
    private Queue<Double> mValues = new LinkedBlockingQueue<Double>();

    public RollingAverage() {
    }

    public RollingAverage(int windowSize) {
        mWindowSize = windowSize;
    }

    public synchronized void add(double value) {
        mValues.add(value);
        if(mValues.size() > mWindowSize) {
            mValues.poll();
        }
    }

    public double getAverage() {
        double sum = 0;
        for(Double value : mValues) {
            sum += value;
        }
        return sum / mValues.size();
    }
}
