package io.relayr.demo.thermometer.directconnection;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;

import java.util.Arrays;
import java.util.List;

import io.relayr.RelayrSdk;
import io.relayr.ble.BleDevice;
import io.relayr.ble.BleDeviceMode;
import io.relayr.ble.BleDeviceType;
import io.relayr.ble.service.BaseService;
import io.relayr.ble.service.DirectConnectionService;
import io.relayr.model.Reading;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class ThermometerDemoActivity extends Activity {

    private TextView mThOutput;
    private TextView mThError;
    private ProgressBar mProgressBar;

    private Subscription mThuSubscription;
    private Subscription mDirectConnSubscription;
    private Subscription mBleDevicesSubscription;

    private BluetoothAdapter mBTAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thermometer_demo);

        mProgressBar = (ProgressBar) findViewById(R.id.thu_progress);

        mThOutput = (TextView) findViewById(R.id.thermometer_output);
        mThError = (TextView) findViewById(R.id.thermometer_error);
    }

    @Override
    protected void onResume() {
        super.onResume();

        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mBTAdapter = btManager.getAdapter();
        if (mBTAdapter != null && !mBTAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableIntent);
        } else {
            discoverThermometer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mBTAdapter != null && mBTAdapter.isEnabled()) {
            mBTAdapter.disable();
        }

        unSubscribeToUpdates();
    }

    public void discoverThermometer() {
        findViewById(R.id.thermometer_scanning).setVisibility(View.VISIBLE);

        // Search for WunderBar temp/humidity sensors
        // Take first that enables direct connection
        mBleDevicesSubscription = RelayrSdk.getRelayrBleSdk().scan(Arrays.asList(BleDeviceType.WunderbarHTU))
                .filter(new Func1<List<BleDevice>, Boolean>() {
                    @Override
                    public Boolean call(List<BleDevice> bleDevices) {
                        for (BleDevice device : bleDevices) {
                            return device.getMode() == BleDeviceMode.DIRECT_CONNECTION;
                        }

                        return false;
                    }
                })
                .take(1)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<List<BleDevice>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        mThError.setText(R.string.sensor_discovery_error);
                        hideProgressBar();
                    }

                    @Override
                    public void onNext(List<BleDevice> bleDevices) {
                        findViewById(R.id.thermometer_found).setVisibility(View.VISIBLE);

                        // We can stop scanning, since we've found some sensors
                        RelayrSdk.getRelayrBleSdk().stop();

                        connectToThermometer(bleDevices.get(0));
                    }
                });
    }

    //Get DirectConnectionService by connecting to device
    private void connectToThermometer(BleDevice device) {
        mThuSubscription = device.connect()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<BaseService>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        mThError.setText(R.string.sensor_connection_error);
                        hideProgressBar();
                    }

                    @Override
                    public void onNext(BaseService service) {
                        findViewById(R.id.thermometer_connected).setVisibility(View.VISIBLE);

                        final DirectConnectionService directConnection = (DirectConnectionService) service;
                        startReadingFromThermometer(directConnection);
                    }
                });
    }

    // Start reading temperature values from the passed DirectConnectionService
    private void startReadingFromThermometer(DirectConnectionService service) {
        mDirectConnSubscription = service.getReadings()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        mThError.setText(R.string.sensor_reading_error);
                        hideProgressBar();
                    }

                    @Override
                    public void onNext(String s) {
                        hideProgressBar();

                        Reading reading = new Gson().fromJson(s, Reading.class);

                        mThOutput.setText(reading.temp + "°C");
                    }
                });
    }

    private void hideProgressBar() {
        if (mProgressBar != null && mProgressBar.getVisibility() == View.VISIBLE) {
            mProgressBar.setVisibility(View.GONE);
        }
    }

    private void unSubscribeToUpdates() {
        if (isSubscribed(mBleDevicesSubscription)) {
            mBleDevicesSubscription.unsubscribe();
        }
        if (isSubscribed(mThuSubscription)) {
            mThuSubscription.unsubscribe();
        }
        if (isSubscribed(mDirectConnSubscription)) {
            mDirectConnSubscription.unsubscribe();
        }
    }

    private static boolean isSubscribed(Subscription subscription) {
        return subscription != null && !subscription.isUnsubscribed();
    }
}
