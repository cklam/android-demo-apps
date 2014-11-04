package android.relayr.thermometerdirectconnection;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.relayr.RelayrSdk;
import io.relayr.ble.BleDevice;
import io.relayr.ble.BleDeviceMode;
import io.relayr.ble.BleDeviceType;
import io.relayr.ble.service.BaseService;
import io.relayr.ble.service.DirectConnectionService;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;


public class ThermometerDemoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thermometer_demo);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.thermometer_demo, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Search for a thermometer in DirectConnection mode. If found, connect to it and start reading values
        DiscoverThermometer();
    }

    // Discover any paired Thermometer sensor modules. If one is found, connect in DirectConnection mode
    public void DiscoverThermometer() {

        // Only search for Temperature/Humidity sensor modules
        ArrayList<BleDeviceType> deviceTypes = new ArrayList<BleDeviceType>();
        deviceTypes.add(BleDeviceType.WunderbarHTU);

        // Search for any WunderBar sensors (in this case, only temp/humidit
        RelayrSdk.getRelayrBleSdk().scan(deviceTypes)
                .filter(new Func1<List<BleDevice>, Boolean>() {  // We only want temperature sensors that are in DirectConnection mode
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
                .take(1)     // We only want to execute this connection code once. Call it once we have the first device update that includes a temperature sensor in DirectConnection mode.
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<List<BleDevice>>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(ThermometerDemoActivity.this, R.string.sensor_discovery_error, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(List<BleDevice> bleDevices) {

                        // We can stop scanning, since we've found some sensors
                        RelayrSdk.getRelayrBleSdk().stop();

                        // Take the first DirectConnection thermometer that we find. In a production application, you'd probably
                        // want to identify the sensor more specifically
                        BleDevice device = null;
                        for(BleDevice possibleDevice : bleDevices) {
                            if(possibleDevice.getMode() == BleDeviceMode.DIRECT_CONNECTION) {
                                device = possibleDevice;
                                break;
                            }
                        }

                        ConnectToThermometer(device);
                    }
                });
    }

    // Actually perform the connection, start reaing data if successful.
    private void ConnectToThermometer(BleDevice device) {
        device.connect()      // Connect to the first device in the list. Naive implementation, your device could be one of many temperature sensors in a given area
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<BaseService>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(ThermometerDemoActivity.this, R.string.sensor_connection_error, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(BaseService service) {
                        final DirectConnectionService directConnection = (DirectConnectionService) service;
                        directConnection.getReadings()      // Start reading data from the temperature sensor
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Subscriber<String>() {
                                    @Override
                                    public void onCompleted() {

                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        Toast.makeText(ThermometerDemoActivity.this, R.string.sensor_reading_error, Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onNext(String s) {

                                        // Get the sensor data from the JSON, display the value in the application
                                        JSONObject readingJSON;
                                        String reading = null;
                                        try {
                                            readingJSON = new JSONObject(s);
                                            reading = readingJSON.get("temp").toString();
                                        } catch (Exception e) {
                                            Log.d("JSON Exception", "JSON Exception when reading data from reading string. Detail: " + e.toString());
                                        }

                                        ((TextView) findViewById(R.id.temperatureOutput)).setText(reading);
                                    }
                                });
                    }
                });
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_thermometer_demo, container, false);
            return rootView;
        }
    }
}
