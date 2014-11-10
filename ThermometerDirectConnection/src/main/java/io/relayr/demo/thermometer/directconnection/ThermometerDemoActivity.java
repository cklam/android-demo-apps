package io.relayr.demo.thermometer.directconnection;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

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
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public class ThermometerDemoActivity extends Activity {

    private TextView mThermometerOutput;
    private TextView mThermometerError;

    private Subscription mThuSubscription = Subscriptions.empty();
    private Subscription mDirectConnSubscription = Subscriptions.empty();
    private Subscription mBleDevicesSubscription = Subscriptions.empty();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_thermometer_demo);

        mThermometerOutput = (TextView) findViewById(R.id.thermometer_output);
        mThermometerError = (TextView) findViewById(R.id.thermometer_error);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!RelayrSdk.isBleSupported()) {
            Toast.makeText(this, getString(R.string.bt_not_supported), Toast.LENGTH_SHORT).show();
        } else {
            if (RelayrSdk.isBleAvailable()) {
                discoverThermometer();
            } else {
                RelayrSdk.promptUserToActivateBluetooth(this);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unSubscribeToUpdates();
    }

    public void discoverThermometer() {
        // Search for WunderBar temp/humidity sensors
        // Take first that enables direct connection
        mBleDevicesSubscription = RelayrSdk.getRelayrBleSdk()
                .scan(Arrays.asList(BleDeviceType.WunderbarHTU))
                .filter(new Func1<List<BleDevice>, Boolean>() {
                    @Override
                    public Boolean call(List<BleDevice> bleDevices) {
                        boolean directMode = false;

                        for (BleDevice device : bleDevices) {
                            if (device.getMode() == BleDeviceMode.DIRECT_CONNECTION) {
                                directMode = true;
                            }
                        }

                        return directMode;
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
                        mThermometerError.setText(R.string.sensor_discovery_error);
                    }

                    @Override
                    public void onNext(List<BleDevice> bleDevices) {
                        // We can stop scanning, since we've found some sensors
                        RelayrSdk.getRelayrBleSdk().stop();

                        connectToThermometer(bleDevices.get(0));
                    }
                });
    }

    //Get DirectConnectionService by connecting to device
    private void connectToThermometer(final BleDevice device) {
        mThuSubscription = device.connect()
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
//                        device.disconnect();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<BaseService>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        mThermometerError.setText(R.string.sensor_connection_error);
                    }

                    @Override
                    public void onNext(BaseService service) {
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
                        mThermometerError.setText(R.string.sensor_reading_error);
                    }

                    @Override
                    public void onNext(String s) {
                        Reading reading = new Gson().fromJson(s, Reading.class);

                        mThermometerOutput.setText("" + reading.temp);
                    }
                });
    }

    private void unSubscribeToUpdates() {
        mBleDevicesSubscription.unsubscribe();
        mThuSubscription.unsubscribe();
        mDirectConnSubscription.unsubscribe();
    }
}
