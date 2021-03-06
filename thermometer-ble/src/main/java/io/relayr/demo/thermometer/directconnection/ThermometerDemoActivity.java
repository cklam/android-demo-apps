package io.relayr.demo.thermometer.directconnection;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

import io.relayr.android.RelayrSdk;
import io.relayr.android.ble.BleDevice;
import io.relayr.android.ble.BleDeviceMode;
import io.relayr.android.ble.service.BaseService;
import io.relayr.android.ble.service.DirectConnectionService;
import io.relayr.java.ble.BleDeviceType;
import io.relayr.java.model.action.Reading;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

public class ThermometerDemoActivity extends Activity {

    private TextView mThermometerOutput;
    private TextView mThermometerError;

    private Subscription mScannerSubscription = Subscriptions.empty();
    private Subscription mDeviceSubscription = Subscriptions.empty();

    private boolean mStartedScanning;
    private BleDevice mDevice;

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
        } else if (!RelayrSdk.isBleAvailable()) {
            RelayrSdk.promptUserToActivateBluetooth(this);
        } else if (!mStartedScanning && mDevice == null) {
            mStartedScanning = true;
            discoverThermometer();
        }
    }

    @Override
    protected void onDestroy() {
        unSubscribeToUpdates();
        disconnectBluetooth();

        super.onDestroy();
    }

    public void discoverThermometer() {
        // Search for WunderBar temp/humidity sensors and take first that is direct connection mode
        mScannerSubscription = RelayrSdk.getRelayrBleSdk()
                .scan(Arrays.asList(BleDeviceType.WunderbarHTU))
                .filter(new Func1<List<BleDevice>, Boolean>() {
                    @Override
                    public Boolean call(List<BleDevice> bleDevices) {
                        for (BleDevice device : bleDevices) {
                            if (device.getMode() == BleDeviceMode.DIRECT_CONNECTION) {
                                // We can stop scanning, since we've found a sensor
                                RelayrSdk.getRelayrBleSdk().stop();
                                return true;
                            }
                        }

                        mStartedScanning = false;
                        return false;
                    }
                })
                .map(new Func1<List<BleDevice>, BleDevice>() {
                    @Override
                    public BleDevice call(List<BleDevice> bleDevices) {
                        for (BleDevice device : bleDevices) {
                            if (device.getMode() == BleDeviceMode.DIRECT_CONNECTION) return device;
                        }

                        mStartedScanning = false;
                        return null; // will never happen since it's been filtered out before
                    }
                })
                .take(1)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<BleDevice>() {
                    @Override
                    public void onCompleted() {
                        mStartedScanning = false;
                    }

                    @Override
                    public void onError(Throwable e) {
                        mStartedScanning = false;
                        mThermometerError.setText(R.string.sensor_discovery_error);
                    }

                    @Override
                    public void onNext(BleDevice device) {
                        mThermometerOutput.setText("-.-");
                        subscribeForTemperatureUpdates(device);
                    }
                });
    }

    private void subscribeForTemperatureUpdates(final BleDevice device) {
        mDevice = device;
        mDeviceSubscription = device.connect()
                .flatMap(new Func1<BaseService, Observable<Reading>>() {
                    @Override
                    public Observable<Reading> call(BaseService baseService) {
                        return ((DirectConnectionService) baseService).getReadings();
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        device.disconnect();
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Reading>() {
                    @Override
                    public void onCompleted() {
                        mStartedScanning = false;
                    }

                    @Override
                    public void onError(Throwable e) {
                        mStartedScanning = false;
                        mThermometerError.setText(R.string.sensor_reading_error);
                    }

                    @Override
                    public void onNext(Reading reading) {
                        mThermometerOutput.setText("" + reading.value);
                    }
                });
    }

    private void unSubscribeToUpdates() {
        mScannerSubscription.unsubscribe();
        mDeviceSubscription.unsubscribe();

        if (mDevice != null) mDevice.disconnect();
    }

    private void disconnectBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled()) mBluetoothAdapter.disable();
    }
}
