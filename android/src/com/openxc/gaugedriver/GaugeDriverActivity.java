package com.openxc.gaugedriver;

import java.util.Timer;
import java.util.TimerTask;

import jp.ksksue.driver.serial.FTDriver;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.openxc.VehicleManager;
import com.openxc.measurements.FineOdometer;
import com.openxc.measurements.FuelConsumed;
import com.openxc.measurements.Measurement;
import com.openxc.measurements.SteeringWheelAngle;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.VehicleSpeed;
import com.openxc.remote.VehicleServiceException;

public class GaugeDriverActivity extends Activity {

    private static String TAG = "GaugeDriver";
    private int mTimerPeriod = 10;  //Time between Gauge updates, in milliseconds.

    private VehicleManager mVehicleManager;
    private boolean mIsBound;
    StringBuffer mBuffer;

    private ToggleButton mToggleButton;
    private TextView mStatusText;
    private TextView mSendText;

    private PendingIntent mPermissionIntent;
    UsbManager mUsbManager = null;
    UsbDevice mGaugeDevice = null;
    UsbDeviceConnection mGaugeConnection = null;
    UsbEndpoint mEndpointIn = null;
    UsbEndpoint mEndpointOut = null;
    UsbInterface mGaugeInterface = null;

    private static int mDataUsed = 0;
    private static boolean mNewData = false;

    static double mGaugeMin = 0;
    static double mGaugeRange = 80;

    static boolean mSerialStarted = false;
    static FTDriver mSerialPort = null;

    static private Timer mReceiveTimer = null;
    static private boolean mColorToValue = false;
    private CheckBox mColorCheckBox;
    private SeekBar mColorSeekBar;
    static private int mLastColor = 0;

    public static final String ACTION_USB_PERMISSION =
            "com.ford.openxc.USB_PERMISSION";

    static double mSpeed = 0.0;
    static double mLastOdo = 0.0;
    static double mCurrentOdo = 0.0;
    static double mLastFuel = 0.0;
    static double mCurrentFuel = 0.0;
    static double mLastMPG = 0.0;
    static double mSteeringWheelAngle = 0.0;

    VehicleSpeed.Listener mSpeedListener = new VehicleSpeed.Listener() {
        public void receive(Measurement measurement) {
            final VehicleSpeed speed = (VehicleSpeed) measurement;
            mSpeed = speed.getValue().doubleValue();
            if(mDataUsed == 0)
                mNewData = true;
        }
    };

    FuelConsumed.Listener mFuelConsumedListener = new FuelConsumed.Listener() {
        public void receive(Measurement measurement) {
            final FuelConsumed fuel = (FuelConsumed) measurement;
            mCurrentFuel = fuel.getValue().doubleValue();
            if(mDataUsed == 1)
                mNewData = true;
            double mpg = ((mCurrentOdo - mLastOdo) / 1600) / ((mCurrentFuel - mLastFuel) * 0.264172);  //Converting from m / l to mi / gal.
            mLastFuel = mCurrentFuel;
            mLastOdo = mCurrentOdo;

            mLastMPG = (mLastMPG * 9.0 + mpg)/10.0;
        }
    };

   FineOdometer.Listener mFineOdometerListener = new FineOdometer.Listener() {
        public void receive(Measurement measurement) {
            final FineOdometer odometer = (FineOdometer) measurement;
            mCurrentOdo = odometer.getValue().doubleValue();
        }
    };

    SteeringWheelAngle.Listener mSteeringWheelListener = new SteeringWheelAngle.Listener() {
        public void receive(Measurement measurement) {
            final SteeringWheelAngle angle = (SteeringWheelAngle) measurement;
            mSteeringWheelAngle = angle.getValue().doubleValue();
            if(mDataUsed == 2)
                mNewData = true;
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            Log.i(TAG, "Bound to VehicleManager");
            mVehicleManager = ((VehicleManager.VehicleBinder)service).getService();

            try {
                mVehicleManager.addListener(SteeringWheelAngle.class,
                        mSteeringWheelListener);
                mVehicleManager.addListener(VehicleSpeed.class,
                        mSpeedListener);
                mVehicleManager.addListener(FuelConsumed.class,
                        mFuelConsumedListener);
                mVehicleManager.addListener(FineOdometer.class,
                        mFineOdometerListener);
            } catch(VehicleServiceException e) {
                 Log.w(TAG, "Couldn't add listeners for measurements", e);
            } catch(UnrecognizedMeasurementTypeException e) {
                 Log.w(TAG, "Couldn't add listeners for measurements", e);
            }
            mIsBound = true;
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            Log.w(TAG, "VehicleService disconnected unexpectedly");
            mVehicleManager = null;
            mIsBound = false;
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Log.i(TAG, "Gauge Driver created");

        Intent intent = new Intent(this, VehicleManager.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        mColorCheckBox = (CheckBox) findViewById(R.id.checkBoxColor);
        mColorCheckBox.setChecked(mColorToValue);

        mColorSeekBar = (SeekBar) findViewById(R.id.seekBarColor);
        mColorSeekBar.setMax(259);
        mColorSeekBar.setEnabled(!mColorToValue);
        mColorSeekBar.setProgress(mLastColor);

        mColorSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                if(!mColorToValue){
                    writeStringToSerial( "<" + String.format("%03d", progress) + ">");
                    mLastColor = progress;
                }
            }
            public void onStartTrackingTouch(SeekBar seekbar) {}
            public void onStopTrackingTouch(SeekBar seekbar) {}
        });

        mStatusText = (TextView) findViewById(R.id.textViewStatus);
        switch (mDataUsed){
        case 0:
            mStatusText.setText("Using Vehicle Speed Data");
            break;
        case 1:
            mStatusText.setText("Using Vehicle Mileage Data");
            break;
        case 2:
            mStatusText.setText("Using Steering Wheel Angle Data");
            break;
        default:
            Log.d(TAG, "mDataUsed got screwed up somehow.  Fixing in onCreate...");
            mStatusText.setText("Using Vehicle Speed Data");
            mDataUsed = 0;
        }

        mSendText = (TextView) findViewById(R.id.editTextManualData);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        mPermissionIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        this.registerReceiver(mBroadcastReceiver, filter);

        if(mSerialPort == null){
            mSerialPort = new FTDriver(mUsbManager);
            mSerialPort.setPermissionIntent(mPermissionIntent);
            mSerialStarted = mSerialPort.begin(9600);
            if (!mSerialStarted)
            {
                Log.d(TAG, "mSerialPort.begin() failed.");
            } else{
                Log.d(TAG, "mSerialPort.begin() success!.");
                if(mReceiveTimer == null)   //Start the updates.
                    onTimerToggle(null);
            }
        }

        if (mReceiveTimer != null)  //If the timer is running
        {
            onTimerToggle(null);
            onTimerToggle(null);    //Reset the timer so the slider updates are pointing at the right Activity.
        }
        
        mToggleButton = (ToggleButton) findViewById(R.id.toggleButtonTimer);
        if (mReceiveTimer != null)  {  //If the timer is running
            mToggleButton.setChecked(true);
        } else {
            mToggleButton.setChecked(false);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(
                        UsbManager.EXTRA_DEVICE);

                if(intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    mSerialStarted = mSerialPort.begin(9600);
                    if (mSerialStarted)
                    {
                        if(mReceiveTimer == null)   //Start the updates.
                            onTimerToggle(null);
                    } else
                    {
                        Log.d(TAG, "mSerialPort.begin() failed AGAIN.");
                    }
                } else {
                    Log.i(TAG, "User declined permission for device " + device);
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        bindService(new Intent(this, VehicleManager.class),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    public void onPause() {
        super.onPause();
        if(mIsBound) {
            //Log.i(TAG, "Unbinding from vehicle service");
            //unbindService(mConnection);
            //mIsBound = false;
        }
    }

    private void UpdateStatus(String newMessage) {
        final CharSequence outCS = newMessage;
        runOnUiThread(new Runnable() {
            public void run() {
                mStatusText.setText(outCS);
            }
        });
    }

    private void ReceiveTimerMethod()
    {
        if(!mNewData)
            return;

        mNewData = false;

        //Send data
        double dValue = 0.0;
        switch(mDataUsed) {
        case 0:  //Speed
            dValue = mSpeed * 0.621371;  //Converting from kph to mph.
            UpdateStatus("Speed: " + dValue);
            break;
        case 1:  //mpg
            dValue = mLastMPG;
            UpdateStatus("Mileage: " + dValue);
            break;
        case 2:  //Steering wheel angle
            dValue = mSteeringWheelAngle + 90.0;
            //Make sure we're never sending a negative number here...
            if (dValue < 0.0)
                dValue = 0.0;
            else if (dValue > 180.0)
                dValue = 180.0;
            dValue /= 1.81;
            UpdateStatus("Steering wheel angle: " + dValue);
            break;
        default:
            Log.d(TAG, "mDataUsed got screwed up.  Fixing in ReceiveTimerMethod...");
            mDataUsed = 0;
            dValue = mSpeed;
            runOnUiThread(new Runnable() {
                public void run() {
                    mStatusText.setText("Using Vehicle Speed Data");
                }
            });
        }

        double dPercent = (dValue - mGaugeMin) / mGaugeRange;
        if(mColorToValue) {
            if (dPercent > 1)
                dPercent = 1;
            else if (dPercent < 0)
                dPercent = 0;

            int thisColor = 0;
            switch (mDataUsed) {
            //colors: 0-259.  Red == 180, Green == 255, Blue = 60.
            case 0: //Speed: 0mph = green, 80mph = red.
                thisColor = 255 - (int)(dPercent * 75.0);
                break;
            case 1: //Mileage: 50mpg = green, 0mpg = red.
                thisColor = 180 + (int)(dPercent * 75.0);
                break;
            case 2: //Steering wheel angle:  Sweep through the spectrum.
                thisColor = (int)(dPercent * 259.0);
                break;
            default:
                Log.d(TAG, "mDataUsed got messed up in ReceiveTimerMethod, in the percentage code!");
            }

            if (thisColor != mLastColor) {
                mLastColor = thisColor;
                writeStringToSerial( "<" + String.format("%03d", thisColor) + ">");

                runOnUiThread(new Runnable() {
                    public void run() {
                        mColorSeekBar.setProgress(mLastColor);
                    }
                });
            }
        }

        int iPercent = (int)(100.0 * dPercent);

        if(iPercent > 99)
        {
            iPercent = 99;
        } else if (iPercent < 0)
        {
            iPercent = 0;
        }

        int value = (int)dValue;
        value %= 100;  //We've only got two digits to work with.

        writeStringToSerial("(" + String.format("%02d", value) + "|" +
                String.format("%02d", iPercent) + ")");
    }

    private void writeStringToSerial(String outString){
        char[] outMessage = outString.toCharArray();
        byte outBuffer[] = new byte[128];
        for(int i=0; i<outString.length(); i++)
        {
            outBuffer[i] = (byte)outMessage[i];
        }
        try {
            mSerialPort.write(outBuffer,  outString.length());
        } catch (Exception e) {
            Log.d(TAG, "mSerialPort.write() just threw an exception.  Is the cable plugged in?");
        }
    }

    public void onExit(View view){
        if (mReceiveTimer != null){
            mReceiveTimer.cancel();
        }
        if (mSerialPort != null){
            mSerialPort.end();
        }
        if(mIsBound) {
            Log.i(TAG, "Unbinding from vehicle service before exit");
            unbindService(mConnection);
            mIsBound = false;
        }
        finish();
        System.exit(0);
    }

    public void onTimerToggle(View view) {
        if(mReceiveTimer == null)
        {
            mReceiveTimer = new Timer();

            mReceiveTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    ReceiveTimerMethod();
                }
            }, 0, mTimerPeriod);
            Log.d(TAG, "Timer has been initialized.");
        } else
        {
            mReceiveTimer.cancel();
            mReceiveTimer.purge();
            mReceiveTimer = null;
            Log.d(TAG, "Timer has been canceled.");
        }
    }

    public void onSpeedClick(View view) {
        mDataUsed = 0;

        mStatusText.setText("Using Vehicle Speed Data");
        mGaugeMin = 0.0;
        mGaugeRange = 120.0;

        mNewData = true;
    }

    public void onMPGClick(View view) {
        mDataUsed = 1;

        //Reset all our data.
        mLastOdo = mCurrentOdo;
        mLastFuel = mCurrentFuel;

        mStatusText.setText("Using Vehicle Mileage Data");

        mGaugeMin = 0.0;
        mGaugeRange = 50.0;

        mNewData = true;
    }

    public void onSteeringClick(View view) {
        mDataUsed = 2;

        mStatusText.setText("Using SteeringWheel Angle Data");
        mGaugeMin = 0.0;
        mGaugeRange = 100.0;

        mNewData = true;
    }

    public void onColorCheckBoxClick(View view) {
        mColorToValue = mColorCheckBox.isChecked();

        mColorSeekBar.setEnabled(!mColorToValue);
    }

    public void onSend(View view) {
        writeStringToSerial(mSendText.getText().toString());
    }
}
