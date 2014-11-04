package android.relayr.thermometerdirectconnection;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import io.relayr.RelayrSdk;
import io.relayr.ble.BleDevice;
import io.relayr.ble.BleDeviceMode;
import io.relayr.ble.BleDeviceType;
import io.relayr.ble.service.BaseService;
import io.relayr.ble.service.DirectConnectionService;
import io.relayr.model.Reading;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class ThermometerDemoActivity extends Activity {

    private TextView readingOutput = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thermometer_demo);

        readingOutput = (TextView) findViewById(R.id.temperatureOutput);

        discoverThermometer();
    }

    // Discover any paired Thermometer sensor modules.
    // If one is found, connect in DirectConnection mode
    public void discoverThermometer() {

        // Only search for Temperature/Humidity sensor modules
        ArrayList<BleDeviceType> deviceTypes = new ArrayList<BleDeviceType>();
        deviceTypes.add(BleDeviceType.WunderbarHTU);

        // Search for any WunderBar sensors (in this case, only temp/humidity
        RelayrSdk.getRelayrBleSdk().scan(deviceTypes)
                .filter(new Func1<List<BleDevice>, Boolean>() {
                    // Filter for only sensors in DirectConnection mode
                    @Override
                    public Boolean call(List<BleDevice> bleDevices) {
                        for(BleDevice device : bleDevices) {
                            if(device.getMode() == BleDeviceMode.DIRECT_CONNECTION) {
                                return true;
                            }
                        }

                        return false;
                    }
                })
                .take(1)     // Only attempt to connect the first time a sensor is found
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<List<BleDevice>>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(ThermometerDemoActivity.this,
                                R.string.sensor_discovery_error, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(List<BleDevice> bleDevices) {

                        // We can stop scanning, since we've found some sensors
                        RelayrSdk.getRelayrBleSdk().stop();

                        // Take the first DirectConnection thermometer that we find.
                        // In a production application, you'd probably want to identify the
                        // sensor more specifically
                        BleDevice device = null;
                        for(BleDevice possibleDevice : bleDevices) {
                            if(possibleDevice.getMode() == BleDeviceMode.DIRECT_CONNECTION) {
                                device = possibleDevice;
                                break;
                            }
                        }

                        connectToThermometer(device);
                    }
                });
}

    // Actually perform the connection, start reading data if successful.
    private void connectToThermometer(BleDevice device) {
        device.connect()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<BaseService>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(ThermometerDemoActivity.this,
                                R.string.sensor_connection_error, Toast.LENGTH_SHORT).show();
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
        service.getReadings()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(ThermometerDemoActivity.this,
                                R.string.sensor_reading_error, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(String s) {

                        // Get the sensor data from the JSON, display the value in the application
                        Reading reading = new Gson().fromJson(s, Reading.class);
                        readingOutput.setText(reading.temp + "°C");
                    }
                });
    }
}
