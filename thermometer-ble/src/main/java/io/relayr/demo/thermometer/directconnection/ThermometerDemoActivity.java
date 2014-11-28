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
import rx.Observable;
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

    private boolean mStartedScanningForDevices;

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
        } else if (RelayrSdk.isBleAvailable()) {
            RelayrSdk.promptUserToActivateBluetooth(this);
        } else if (!mStartedScanningForDevices) {
            discoverThermometer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unSubscribeToUpdates();
    }

    public void discoverThermometer() {
        // Search for WunderBar temp/humidity sensors and take first that is direct connection mode
        mStartedScanningForDevices = true;
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
                        return false;
                    }
                })
                .map(new Func1<List<BleDevice>, BleDevice>() {
                    @Override
                    public BleDevice call(List<BleDevice> bleDevices) {
                        for (BleDevice device : bleDevices) {
                            if (device.getMode() == BleDeviceMode.DIRECT_CONNECTION) {
                                return device;
                            }
                        }
                        return null; // will never happen since it's been filtered out before
                    }
                })
                .take(1)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<BleDevice>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        mThermometerError.setText(R.string.sensor_discovery_error);
                    }

                    @Override
                    public void onNext(BleDevice device) {
                        subscribeForTemperatureUpdates(device);
                    }
                });
    }

    private void unSubscribeToUpdates() {
        mScannerSubscription.unsubscribe();
        mDeviceSubscription.unsubscribe();
    }

    private void subscribeForTemperatureUpdates(final BleDevice device) {
        mDeviceSubscription = device
                .connect()
                .flatMap(new Func1<BaseService, Observable<String>>() {
                    @Override
                    public Observable<String> call(BaseService baseService) {
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
}
