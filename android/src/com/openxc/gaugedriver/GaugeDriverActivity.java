package com.openxc.gaugedriver;

import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

import jp.ksksue.driver.serial.FTDriver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.openxc.NoValueException;
import com.openxc.VehicleManager;
import com.openxc.measurements.FuelConsumed;
import com.openxc.measurements.Measurement;
import com.openxc.measurements.Odometer;
import com.openxc.measurements.SteeringWheelAngle;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.VehicleSpeed;
import com.openxc.remote.VehicleServiceException;

public class GaugeDriverActivity extends Activity {
    private final String TAG = "GaugeDriver";
    private final int GAUGE_UPDATE_PERIOD_MS = 10;
    private final double MPH_PER_KPH = 0.621371;
    private final double MILES_PER_KM = 0.621371;
    private final double GALLONS_PER_LITER = 0.264172;
    private final double KM_L_TO_MPG_MULTIPLIER =
            MILES_PER_KM / GALLONS_PER_LITER;

    private TextView mStatusText;
    private TextView mSendText;

    private VehicleManager mVehicleManager;
    private UsbManager mUsbManager;
    private FTDriver mSerialPort;

    private Class<? extends Measurement> mActiveDataType = VehicleSpeed.class;

    private double mGaugeMin = 0;
    private double mGaugeRange = 80;

    private Timer mReceiveTimer;
    private boolean mColorToValue;
    private CheckBox mColorCheckBox;
    private SeekBar mColorSeekBar;
    private int mLastColor = 0;

    private Odometer mLastOdometer;
    private FuelConsumed mLastFuelConsumed;
    private RollingAverage mRollingMpg = new RollingAverage();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Log.i(TAG, "Gauge Driver created");

        mColorCheckBox = (CheckBox) findViewById(R.id.checkBoxColor);
        mColorCheckBox.setChecked(mColorToValue);

        mColorSeekBar = (SeekBar) findViewById(R.id.seekBarColor);
        mColorSeekBar.setMax(259);
        mColorSeekBar.setEnabled(!mColorToValue);
        mColorSeekBar.setProgress(mLastColor);

        mColorSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress,
                    boolean fromUser) {
                if(!mColorToValue){
                    writeStringToSerial( "<" +
                        String.format("%03d", progress)+ ">");
                    mLastColor = progress;
                }
            }
            public void onStartTrackingTouch(SeekBar seekbar) {}
            public void onStopTrackingTouch(SeekBar seekbar) {}
        });

        mStatusText = (TextView) findViewById(R.id.textViewStatus);
        String measurementName = "unknown";
        try {
            Method genericNameMethod = mActiveDataType.getMethod(
                    "getGenericName");
            measurementName = (String) genericNameMethod.invoke(
                    mActiveDataType);
        } catch(NoSuchMethodException e) {
        } catch(Exception e) {
        }
        mStatusText.setText("Using " + measurementName);

        mSendText = (TextView) findViewById(R.id.editTextManualData);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mBroadcastReceiver, filter);

        bindService(new Intent(this, VehicleManager.class),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        connectToDevice();
        
        ToggleButton mToggleButton = (ToggleButton) findViewById
                (R.id.toggleButtonTimer);
        if(mReceiveTimer != null)  {  //If the timer is running
            mToggleButton.setChecked(true);
        } else {
            mToggleButton.setChecked(false);
        }
    }

    public void onExit(View view){
        if(mReceiveTimer != null) {
            mReceiveTimer.cancel();
        }

        if(mSerialPort != null) {
            mSerialPort.end();
        }

        if(mVehicleManager != null) {
            Log.i(TAG, "Unbinding from vehicle service before exit");
            unbindService(mConnection);
            mVehicleManager = null;
        }

        finish();
        System.exit(0);
    }

    public void onTimerToggle(View view) {
        configureTimer(((ToggleButton) view).isChecked());
    }

    public void onSpeedClick(View view) {
        mActiveDataType = VehicleSpeed.class;
        mStatusText.setText("Using Vehicle Speed Data");
        mGaugeMin = 0.0;
        mGaugeRange = 120.0;
    }

    public void onMPGClick(View view) {
        mActiveDataType = FuelConsumed.class;
        mStatusText.setText("Using Vehicle Mileage Data");
        mGaugeMin = 0.0;
        mGaugeRange = 50.0;
    }

    public void onSteeringClick(View view) {
        mActiveDataType = SteeringWheelAngle.class;
        mStatusText.setText("Using SteeringWheel Angle Data");
        mGaugeMin = 0.0;
        mGaugeRange = 100.0;
    }

    public void onColorCheckBoxClick(View view) {
        mColorToValue = mColorCheckBox.isChecked();
        mColorSeekBar.setEnabled(!mColorToValue);
    }

    public void onSend(View view) {
        writeStringToSerial(mSendText.getText().toString());
    }

    private void updateStatus(String newMessage) {
        final CharSequence outCS = newMessage;
        runOnUiThread(new Runnable() {
            public void run() {
                mStatusText.setText(outCS);
            }
        });
    }

    private void checkUpdatedData() {
        if(mVehicleManager == null) {
            Log.d(TAG, "Vehicle manager not connected, not updating");
            return;
        }

        double value = 0.0;
        if(mActiveDataType == VehicleSpeed.class) {
            String error = "Couldn't get current speed, can't calulate fuel efficiency";
            try {
                VehicleSpeed speed = (VehicleSpeed) mVehicleManager.get(VehicleSpeed.class);
                value = speed.getValue().doubleValue() * MPH_PER_KPH;
                updateStatus("Speed: " + value);
            } catch(UnrecognizedMeasurementTypeException e) {
                Log.w(TAG, error, e);
            } catch(NoValueException e) {
                Log.w(TAG, error, e);
            }
        } else if(mActiveDataType == FuelConsumed.class) {
            value = mRollingMpg.getAverage();
            updateStatus("Mileage: " + value);
        } else if(mActiveDataType == SteeringWheelAngle.class) {
            String error = "Couldn't get current speed, can't calulate fuel efficiency";
            try {
                SteeringWheelAngle steeringWheelAngle = (SteeringWheelAngle)
                        mVehicleManager.get(SteeringWheelAngle.class);
                value = steeringWheelAngle.getValue().doubleValue() + 90.0;
                //Make sure we're never sending a negative number here...
                if(value < 0.0) {
                    value = 0.0;
                } else if(value > 180.0) {
                    value = 180.0;
                }
                value /= 1.81;
                updateStatus("Steering wheel angle: " + value);
            } catch(UnrecognizedMeasurementTypeException e) {
                Log.w(TAG, error, e);
            } catch(NoValueException e) {
                Log.w(TAG, error, e);
            }
        } else {
            Log.d(TAG, "Active data type unknown, switching to speed");
            mActiveDataType = SteeringWheelAngle.class;
        }

        double percent = (value - mGaugeMin) / mGaugeRange;
        if(percent > 1.0) {
            percent = 1.0;
        } else if(percent < 0.0) {
            percent = 0.0;
        }

        if(mColorToValue) {
            int thisColor = 0;
            // colors: 0-259.  Red == 0, Yellow == 65, Green == 110,
            // Blue == 170, Purple == 260.
            if(mActiveDataType == VehicleSpeed.class) {
                //Speed: 0mph = green, 80mph = red.
                thisColor = 110 - (int)(percent * 110.0);
            } else if(mActiveDataType == FuelConsumed.class) {
                //Mileage: 50mpg = green, 0mpg = red.
                thisColor = (int)(percent * 110.0);
            } else if(mActiveDataType == SteeringWheelAngle.class) {
                //Steering wheel angle:  Sweep through the spectrum.
                thisColor = (int)(percent * 259.0);
            } else {
                Log.d(TAG, "Active data type got messed up, unable to fix");
            }

            if(thisColor != mLastColor) {
                mLastColor = thisColor;
                String colorPacket = "<" +
                    String.format("%03d", thisColor) +">";
                writeStringToSerial(colorPacket);

                runOnUiThread(new Runnable() {
                    public void run() {
                        mColorSeekBar.setProgress(mLastColor);
                    }
                });
            }
        }

        // We've only got two digits to work with.
        percent = Math.min(percent, .99);
        percent = Math.max(percent, 0);
        value = Math.min(value, 99);
        value = Math.max(value, 0);

        String dataPacket = "(" + String.format("%02d", (int)value) + "|" +
            String.format("%02d", (int)(100 * percent)) + ")";
        writeStringToSerial(dataPacket);
    }


    private void configureTimer(boolean enabled) {
        if(mReceiveTimer == null) {
            mReceiveTimer = new Timer();
        }

        if(enabled) {
            mReceiveTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    checkUpdatedData();
                }
            }, 0, GAUGE_UPDATE_PERIOD_MS);
            Log.d(TAG, "Periodic update task started");
        } else {
            mReceiveTimer.cancel();
            mReceiveTimer.purge();
            mReceiveTimer = null;
            Log.d(TAG, "Periodic updates stopped");
        }
    }

    private void connectToDevice() {
        if(mSerialPort == null) {
            mSerialPort = new FTDriver(mUsbManager);
        }

        if(mSerialPort.isConnected()) {
            showToast("Still connected");
            return;
        }

        mSerialPort.begin(9600);
        if(!mSerialPort.isConnected()) {
            Log.d(TAG, "mSerialPort.begin() failed.");
        } else {
            Log.d(TAG, "mSerialPort.begin() success!");
            showToast("Device connected");
            if(mReceiveTimer == null) {
                configureTimer(true);
            }
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) && 
                    mSerialPort.isConnected()) {
                mSerialPort.usbDetached(intent);
                if(!mSerialPort.isConnected()) {
                    showToast("Device disconnected");
                }
            }
        }
    };

    private void recalculateFuelEfficiency(FuelConsumed fuelConsumed,
            Odometer odometer) {
        if(fuelConsumed == null) {
            try {
                fuelConsumed = (FuelConsumed) mVehicleManager.get(
                        FuelConsumed.class);
            } catch(UnrecognizedMeasurementTypeException e) {
                Log.w(TAG, "Couldn't get current fuel consumption value, " +
                        "can't calulate fuel efficiency", e);
            } catch(NoValueException e) {
                Log.w(TAG, "Couldn't get current fuel consumption value, " +
                        "can't calulate fuel efficiency", e);
            }
        }

        if(odometer == null) {
            try {
                odometer = (Odometer) mVehicleManager.get(Odometer.class);
            } catch(UnrecognizedMeasurementTypeException e) {
                Log.w(TAG, "Couldn't get current odometer value, " +
                        "can't calulate fuel efficiency", e);
            } catch(NoValueException e) {
                Log.w(TAG, "Couldn't get current odometer value, " +
                        "can't calulate fuel efficiency", e);
            }
        }

        if(mLastOdometer == null) {
            mLastOdometer = odometer;
        }

        if(mLastFuelConsumed == null) {
            mLastFuelConsumed = fuelConsumed;
        }

        if(odometer != null && fuelConsumed != null && mLastOdometer != null &&
                mLastFuelConsumed != null) {
            // calculate Km / L from measuremnets and convert to Miles / Gallon
            double deltaOdo = odometer.getValue().doubleValue() -
                    mLastOdometer.getValue().doubleValue();
            double deltaFuel = fuelConsumed.getValue().doubleValue() -
                    mLastFuelConsumed.getValue().doubleValue();

            if(deltaOdo > .01 || deltaFuel > .01) {
                mRollingMpg.add((deltaOdo / Math.max(0.000001, deltaFuel))
                        * KM_L_TO_MPG_MULTIPLIER);
                mLastOdometer = odometer;
                mLastFuelConsumed = fuelConsumed;
            }

        }

    }


    private FuelConsumed.Listener mFuelConsumedListener =
            new FuelConsumed.Listener() {
        public void receive(Measurement measurement) {
            recalculateFuelEfficiency((FuelConsumed)measurement, null);
        }
    };

    private Odometer.Listener mFineOdometerListener = new Odometer.Listener() {
        public void receive(Measurement measurement) {
            recalculateFuelEfficiency(null, (Odometer)measurement);
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            Log.i(TAG, "Bound to VehicleManager");
            mVehicleManager = ((VehicleManager.VehicleBinder)service
                    ).getService();

            try {
                mVehicleManager.addListener(FuelConsumed.class,
                        mFuelConsumedListener);
                mVehicleManager.addListener(Odometer.class,
                        mFineOdometerListener);
            } catch(VehicleServiceException e) {
                Log.w(TAG, "Couldn't add listeners for measurements", e);
            } catch(UnrecognizedMeasurementTypeException e) {
                Log.w(TAG, "Couldn't add listeners for measurements", e);
            }

            configureTimer(false);
            configureTimer(true);
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            Log.w(TAG, "VehicleService disconnected unexpectedly");
            mVehicleManager = null;
        }
    };

    private void writeStringToSerial(String outString){
        if(mSerialPort.isConnected()) {
            char[] outMessage = outString.toCharArray();
            byte outBuffer[] = new byte[128];
            for(int i=0; i<outString.length(); i++) {
                outBuffer[i] = (byte)outMessage[i];
            }
            try {
                mSerialPort.write(outBuffer,  outString.length());
            } catch(Exception e) {
                Log.d(TAG, "Unable to open a serial connection - " +
                        "is the cable attached?");
            }
        }
    }
    
    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }
}
