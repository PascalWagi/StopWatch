package com.example.stopwatch;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static android.widget.Toast.LENGTH_SHORT;

public class MainActivity extends AppCompatActivity {


    private static final long SCAN_PERIOD = 10000;
    private static final int REQUEST_FINE_LOCATION = 25;
    private EditText mEditTextInput;
    private EditText mEditTextCooldown;
    private EditText mEditTextFirstIntervall;
    private EditText mEditTextSecondIntervall;
    private EditText mEditTextIntervallCount;

    private TextView mTextViewCountDown;
    private TextView mTextViewWarmUp;
    private TextView mTextViewCooldown;
    private TextView mTextViewFirstIntervall;
    private TextView mTextViewSecondIntervall;
    private TextView mTextViewIntervallCount;
    private TextView mTextViewInfoText;

    private Button mButtonSet;
    private Button mButtonStartPause;
    private Button mButtonReset;

    private SwitchCompat mSwitchBLE;

    private CountDownTimer mCountDownTimer;

    private boolean mTimerIsRunning;

    private long mTimeLeftInMillis;
    private long mStartTimeInMillis;
    private long mStartTimeWarmUpInMillis;
    private long mStartTimeCooldownInMillis;
    private long mStartTimeFirstIntervallInMillis;
    private long mStartTimeSecondIntervallInMillis;
    private long mEndTime;

    private int intervallStatus = 0; //0 -> WarmUp; 1 -> 1. Intervall; 2 -> 2. Intervall; 3 -> Cooldown
    private int intervallCount = 0;
    private int intervallMaximum = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt myGatt;
    private boolean mScanning;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String TAG = "BLE-Activity";
    private Map<String, BluetoothDevice> mScanResults;
    private ScanCallback mScanCallback;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler mHandler;
    private boolean mConnected;
    private BluetoothGattCharacteristic characteristic;
    private BluetoothGattService service;
    private String deviceAdress = "E8:F8:F1:7D:FC:5B";
    private UUID deviceUUID = UUID.fromString("713D0000-503E-4C75-BA94-3148F18D941E");
    private UUID characteristicUUID = UUID.fromString("713D0003-503E-4C75-BA94-3148F18D941E");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEditTextInput = findViewById(R.id.edit_text_input);
        mEditTextCooldown = findViewById(R.id.edit_text_cooldown);
        mEditTextFirstIntervall = findViewById(R.id.edit_text_firstIntervall);
        mEditTextSecondIntervall = findViewById(R.id.edit_text_secondIntervall);
        mEditTextIntervallCount =  findViewById(R.id.edit_text_intervall_count);

        mTextViewCountDown = findViewById(R.id.countdown_text);
        mTextViewWarmUp = findViewById(R.id.text_warmup);
        mTextViewCooldown = findViewById(R.id.text_cooldown);
        mTextViewFirstIntervall = findViewById(R.id.text_firstIntervall);
        mTextViewSecondIntervall = findViewById(R.id.text_secondIntervall);
        mTextViewIntervallCount = findViewById(R.id.text_intervall_count);
        mTextViewInfoText = findViewById(R.id.text_info_text);

        mButtonSet = findViewById(R.id.button_set);
        mButtonStartPause = findViewById(R.id.countdown_start_button);
        mButtonReset = findViewById(R.id.button_reset);

        mButtonSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = mEditTextInput.getText().toString();
                String cooldown = mEditTextCooldown.getText().toString();
                String first_intervall = mEditTextFirstIntervall.getText().toString();
                String second_intervall = mEditTextSecondIntervall.getText().toString();
                String intervall_count = mEditTextIntervallCount.getText().toString();

                if (input.length() == 0 || cooldown.length() == 0 || first_intervall.length() == 0 || second_intervall.length() == 0 || intervall_count.length() == 0) {
                    Toast.makeText(MainActivity.this, "Feld darf nicht leer sein", LENGTH_SHORT).show();
                    return;
                }

                long millisInput = Long.parseLong(input) * 1000; // * 60000
                long millisCooldown = Long.parseLong(cooldown) * 1000; // * 60000
                long millisFirstIntervall = Long.parseLong(first_intervall) * 1000;
                long millisSecondIntervall = Long.parseLong(second_intervall) * 1000;
                if (millisInput == 0 || millisCooldown == 0 || millisFirstIntervall == 0 || millisSecondIntervall == 0) {
                    Toast.makeText(MainActivity.this, "Zahl muss positiv sein", LENGTH_SHORT).show();
                    return;
                }
                intervallMaximum = Integer.parseInt(intervall_count);

                mStartTimeCooldownInMillis = millisCooldown;
                mStartTimeWarmUpInMillis = millisInput;
                mStartTimeFirstIntervallInMillis = millisFirstIntervall;
                mStartTimeSecondIntervallInMillis = millisSecondIntervall;

                setTimer(millisInput);
                mEditTextInput.setText("");
                mEditTextCooldown.setText("");
                mEditTextFirstIntervall.setText("");
                mEditTextSecondIntervall.setText("");
                mEditTextIntervallCount.setText("");
            }
        });

        mButtonStartPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mTimerIsRunning) {
                    stopTimer();
                } else {
                    startTimer();
                }
            }
        });

        mButtonReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetTimer();
            }
        });

        updateCountDownTextView();

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();


        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu, menu);

        MenuItem item =  menu.findItem(R.id.mySwitch);
        item.setActionView(R.layout.switch_layout);

        mSwitchBLE = item.getActionView().findViewById(R.id.switchBLE);

        mSwitchBLE.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startScan();
                    Toast.makeText(MainActivity.this, "ON", LENGTH_SHORT).show();
                } else {
                    disconnectGattServer();
                    Toast.makeText(MainActivity.this, "OFF", LENGTH_SHORT).show();
                }
            }
        });

        return true;
    }

    private void setTimer(long milliseconds) {
        mStartTimeInMillis = milliseconds;
        endTimer();
        closeKeyboard();
    }

    private void startTimer() {

        mEndTime = System.currentTimeMillis() + mTimeLeftInMillis;

        switch (intervallStatus) {
            case 0:
                mTextViewInfoText.setText("WarmUp");
                break;
            case 1:
                mTextViewInfoText.setText("1. Intervall");
                break;
            case 2:
                mTextViewInfoText.setText("2. Intervall");
                break;
            case 3:
                mTextViewInfoText.setText("Cooldown");
                break;
            default:
                mTextViewInfoText.setText("Training");
        }

        mCountDownTimer = new CountDownTimer(mTimeLeftInMillis, 500) {
            long quartertime = mTimeLeftInMillis / 4;
            long halftime = mTimeLeftInMillis / 2;
            long thirdquartertime = quartertime * 3;

            @Override
            public void onTick(long millisUntilFinished) {
                mTimeLeftInMillis = millisUntilFinished;
                if (intervallStatus == 0 || intervallStatus == 3) {
                    if (mTimeLeftInMillis <= quartertime) {
                        quartertime = Long.MIN_VALUE;
                        multipleVibration(1, 500, 3, 0, 0, 0);
                    }
                    if (mTimeLeftInMillis <= thirdquartertime) {
                        thirdquartertime = Long.MIN_VALUE;
                        multipleVibration(1, 500, 0, 0, 0, 3);
                    }
                }
                if (mTimeLeftInMillis <= halftime) {
                    halftime = Long.MIN_VALUE;
                    multipleVibration(1, 500, 0, 3, 3, 0);

                }
                updateCountDownTextView();
            }

            @Override
            public void onFinish() {
                switch (intervallStatus) {
                    case 0:
                        intervallStatus++;
                        setTimer(mStartTimeFirstIntervallInMillis);
                        multipleVibration(2, 500, 3, 3, 3, 3);
                        stopTimer();
                        startTimer();
                        break;
                    case 1:
                        intervallStatus++;
                        setTimer(mStartTimeSecondIntervallInMillis);
                        stopTimer();
                        startTimer();
                        multipleVibration(2, 500, 3, 0, 0, 0);
                        break;
                    case 2:
                        intervallCount++;
                        if (intervallCount >= intervallMaximum) {
                            intervallStatus = 3;
                            setTimer(mStartTimeCooldownInMillis);
                            multipleVibration(2, 500 , 3, 3, 3, 3);
                        } else {
                            intervallStatus = 1;
                            setTimer(mStartTimeFirstIntervallInMillis);
                            multipleVibration(2, 500, 0, 0, 0, 3);
                        }
                        stopTimer();
                        startTimer();
                        break;
                    case 3:
                        stopTimer();
                        mTimerIsRunning = false;
                        updateWatchInterface();
                        multipleVibration(3, 500, 3, 3, 3, 3);
                        break;
                    default:
                        mTimerIsRunning = false;
                }
                //mTimerIsRunning = false;
            }
        }.start();

        mTimerIsRunning = true;
        updateWatchInterface();
    }

    private void endTimer() {
        mTimeLeftInMillis = mStartTimeInMillis;
        updateCountDownTextView();
        updateWatchInterface();
    }

    private void resetTimer() {
        mTimeLeftInMillis = mStartTimeWarmUpInMillis;
        intervallStatus = 0;
        intervallCount = 0;
        updateCountDownTextView();
        updateWatchInterface();
        mTextViewInfoText.setText("Training");
    }

    private void updateWatchInterface() {
        if (mTimerIsRunning) {
            mEditTextInput.setVisibility(View.INVISIBLE);
            mEditTextCooldown.setVisibility(View.INVISIBLE);
            mEditTextFirstIntervall.setVisibility(View.INVISIBLE);
            mEditTextSecondIntervall.setVisibility(View.INVISIBLE);
            mEditTextIntervallCount.setVisibility(View.INVISIBLE);
            mButtonSet.setVisibility(View.INVISIBLE);
            mButtonReset.setVisibility(View.INVISIBLE);
            mButtonStartPause.setText("Pause");

            mTextViewWarmUp.setVisibility(View.INVISIBLE);
            mTextViewCooldown.setVisibility(View.INVISIBLE);
            mTextViewFirstIntervall.setVisibility(View.INVISIBLE);
            mTextViewSecondIntervall.setVisibility(View.INVISIBLE);
            mTextViewIntervallCount.setVisibility(View.INVISIBLE);

        } else {
            mEditTextInput.setVisibility(View.VISIBLE);
            mEditTextCooldown.setVisibility(View.VISIBLE);
            mEditTextFirstIntervall.setVisibility(View.VISIBLE);
            mEditTextSecondIntervall.setVisibility(View.VISIBLE);
            mEditTextIntervallCount.setVisibility(View.VISIBLE);
            mButtonSet.setVisibility(View.VISIBLE);
            mButtonReset.setVisibility(View.VISIBLE);
            mButtonStartPause.setText("Starten");

            mTextViewWarmUp.setVisibility(View.VISIBLE);
            mTextViewCooldown.setVisibility(View.VISIBLE);
            mTextViewFirstIntervall.setVisibility(View.VISIBLE);
            mTextViewSecondIntervall.setVisibility(View.VISIBLE);
            mTextViewIntervallCount.setVisibility(View.VISIBLE);
        }
    }

    private void stopTimer() {
        mCountDownTimer.cancel();
        mTimerIsRunning = false;
        updateWatchInterface();
    }

    private void updateCountDownTextView() {
        int hours = (int) mTimeLeftInMillis / 1000 / 3600;
        int minutes = (int)  (mTimeLeftInMillis / 1000 % 3600) / 60;
        int seconds = (int)  mTimeLeftInMillis / 1000 % 60;

        String timeLeftFormatted;
        if (hours == 0) {
            timeLeftFormatted = String.format("%02d:%02d", minutes, seconds);

        } else {
            timeLeftFormatted = String.format("%d:%02d:%02d", hours, minutes, seconds);

        }

        mTextViewCountDown.setText(timeLeftFormatted);
    }

    private void closeKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putLong("startTimeCooldown", mStartTimeCooldownInMillis);
        editor.putLong("startTimeFirstIntervall", mStartTimeFirstIntervallInMillis);
        editor.putLong("startTimeSecondIntervall", mStartTimeSecondIntervallInMillis);
        editor.putLong("startTimeInMillis", mStartTimeInMillis);
        editor.putLong("millisLeft", mTimeLeftInMillis);
        editor.putInt("intervallMaximum", intervallMaximum);
        editor.putLong("endTime", mEndTime);
        editor.putBoolean("timerRunning", mTimerIsRunning);

        editor.apply();

        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);

        mStartTimeCooldownInMillis = prefs.getLong("startTimeCooldown", 600000);
        mStartTimeFirstIntervallInMillis = prefs.getLong("startTimeFirstIntervall", 45000);
        mStartTimeSecondIntervallInMillis = prefs.getLong("startTimeSecondIntervall", 15000);
        mTimeLeftInMillis = prefs.getLong("millisLeft", mStartTimeInMillis);
        mTimerIsRunning = prefs.getBoolean("timerRunning", false);
        mStartTimeInMillis = prefs.getLong("startTimeInMillis", 600000);
        intervallMaximum = prefs.getInt("intervallMaximum", 5);

        updateCountDownTextView();
        updateWatchInterface();

        if (mTimerIsRunning) {
            mEndTime = prefs.getLong("endTime", 0);
            mTimeLeftInMillis = mEndTime - System.currentTimeMillis();

            if (mTimeLeftInMillis < 0) {
                mTimeLeftInMillis = 0;
                mTimerIsRunning = false;
                updateCountDownTextView();
                updateWatchInterface();
            } else {
                startTimer();
            }
        }

    }



    private void stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            scanComplete();
        }

        mScanCallback = null;
        mScanning = false;
        mHandler = null;
    }

    private void scanComplete() {
        if (mScanResults.isEmpty()) {
            return;
        }
        for (String deviceAddress : mScanResults.keySet()) {
            Log.d(TAG, "Found device: "+ deviceAddress);
        }
    }

    private boolean hasPermissions() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            requestBluetoothEnable();
            return false;
        } else if (!hasLocationPermissions()){
            requestLocationPermission();
            return false;
        }
        return true;
    }

    private boolean hasLocationPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        Log.d(TAG, "Requested user enables Bluetooth. Try starting the scan again.");
    }




    private class BtleScanCallback extends ScanCallback {

        private Map<String, BluetoothDevice> mScanResults;

        public BtleScanCallback(Map<String, BluetoothDevice> scanResults) {
            mScanResults = scanResults;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addScanResult(result);
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan Failed with code " + errorCode);
        }
        private void addScanResult(ScanResult scanResult) {
            stopScan();
            BluetoothDevice device = scanResult.getDevice();
            connectDevice(device);
        }



    }

    private class GattClientCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_FAILURE) {
                Log.d(TAG, "gatt failure");
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG, "gatt unsuccess");
                disconnectGattServer();
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnected = true;
                gatt.discoverServices();
                Log.d(TAG, "gatt connected / is connected");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG,"gatt disconnected");
                disconnectGattServer();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                return;
            }
            service  = gatt.getService(deviceUUID);
            Log.d("servicePoll", String.valueOf(service!=null) +" "+ (service.getCharacteristic(characteristicUUID)!=null));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("writeSuccess","Characteristic written successfully");
            } else {
                Log.d("writeSuccess","Characteristic write unsuccessful, status: " + status);
                disconnectGattServer();
            }
        }
    }

    private void multipleVibration(int n, int vibrationIntervall, int pwEng1, int pwEng2, int pwEng3, int pwEng4) {
        if (!mConnected) {
            return;
        }
        String msg = writeCharateristic(pwEng1, pwEng2, pwEng3, pwEng4);
        int millis = 0;
        for (int i = 0; i < n; i++) {
            vibrationOn(millis, msg);
            millis += vibrationIntervall;
            vibrationOff(millis);
            millis += vibrationIntervall;
        }


    }
    private void vibrationOn(int millis, String msg) {
        Handler handler = new Handler();
        handler.postDelayed(() -> sendMessage(msg), millis);
    }

    private void vibrationOff(int millis) {
        Handler handler = new Handler();
        handler.postDelayed(() -> sendMessage("00000000"), millis);
    }

    private String writeCharateristic(int powerEngine1, int powerEngine2, int powerEngine3, int powerEngine4) {
        return power(powerEngine1) + power(powerEngine2) + power(powerEngine3) + power(powerEngine4);
    }

    private String power(int power) {
        switch (power) {
            case 0:
                return "00";
            case 1:
                return "77";
            case 2:
                return "AA";
            case 3:
                return "FF";
            default:
                return "00";
        }
    }

    private String engine(int engine, String power) {
        switch (engine) {
            case 0:
                return power + "000000";
            case 1:
                return "00" + power + "0000";
            case 2:
                return "0000" + power + "00";
            case 3:
                return  "000000" + power;
            default:
                return "00000000";
        }
    }


    private void startScan() {
        if (!hasPermissions() || mScanning) {
            Log.d("scan:", "Scan Fehlgeschlagen");
            return;
        }



        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter scanFilter = new ScanFilter.Builder().setDeviceAddress(deviceAdress).build();
        filters.add(scanFilter);
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
        mScanResults = new HashMap<>();
        mScanCallback = new BtleScanCallback(mScanResults);

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mScanning = true;

        mHandler = new Handler();
        mHandler.postDelayed(this::stopScan, SCAN_PERIOD);
        Log.d("Scan:", "Scan complete");
    }

    private void sendMessage(String message) {
        if (!mConnected ) {
            Log.d("sendMessage", "not connected");
            return;
        }

        characteristic = service.getCharacteristic(characteristicUUID);
        Log.d(TAG, characteristic.toString());
        if (characteristic == null) {
            Log.d("sendMessage","mies");
            disconnectGattServer();
            return;
        }

        byte[] messageBytes = hexStringToByteArray(message);

        characteristic.setValue(messageBytes);
        boolean success = myGatt.writeCharacteristic(characteristic);
        if (success) {
            Log.d("sendMessage","Write?");
        }
        Log.d("sendMessage", "ende");
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private void connectDevice(BluetoothDevice device) {
        GattClientCallback gattClientCallback = new GattClientCallback();
        myGatt = device.connectGatt(this, false, gattClientCallback);

        if(myGatt.connect()) {
            Toast.makeText(MainActivity.this, "Device found", LENGTH_SHORT).show();
        }else{
            Toast.makeText(MainActivity.this, "No Ble Device found, please try again", LENGTH_SHORT).show();
        }
        Log.d("GattOn", String.valueOf(myGatt!=null));

    }

    public void disconnectGattServer() {
        mConnected = false;
        Log.d(TAG, "off connected");
        if (myGatt != null) {
            myGatt.disconnect();
            myGatt.close();
        }
    }

}
