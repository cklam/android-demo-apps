package io.hackthehouse;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.List;

import io.relayr.android.RelayrSdk;
import io.relayr.java.model.Device;
import io.relayr.java.model.TransmitterDevice;
import io.relayr.java.model.User;
import io.relayr.java.model.action.Command;
import io.relayr.java.model.action.Reading;
import io.relayr.java.model.models.DeviceModel;
import io.relayr.java.model.models.error.DeviceModelsCacheException;
import io.relayr.java.model.models.error.DeviceModelsException;
import io.relayr.java.model.models.transport.DeviceCommand;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class HthActivity extends Activity {

    private TextView mTextView;
    private Subscription mUserInfoSubscription;
    private TransmitterDevice mDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = View.inflate(this, R.layout.activity_main, null);

        mTextView = (TextView) view.findViewById(R.id.txt);

        setContentView(view);

        if (!RelayrSdk.isUserLoggedIn()) logIn();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (RelayrSdk.isUserLoggedIn())
            getMenuInflater().inflate(R.menu.hth_demo_logged_in, menu);
        else
            getMenuInflater().inflate(R.menu.hth_demo_not_logged_in, menu);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_log_in) {
            logIn();
            return true;
        } else if (item.getItemId() == R.id.action_log_out) {
            logOut();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RelayrSdk.isUserLoggedIn()) updateUiForALoggedInUser();
        else updateUiForANonLoggedInUser();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unSubscribeToUpdates();
    }

    private void logIn() {
        RelayrSdk.logIn(this)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<User>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(HthActivity.this, "Not logged in", Toast.LENGTH_SHORT).show();
                        updateUiForANonLoggedInUser();
                    }

                    @Override
                    public void onNext(User user) {
                        Toast.makeText(HthActivity.this, "Logged in", Toast.LENGTH_SHORT).show();
                        invalidateOptionsMenu();
                    }
                });
    }

    private void logOut() {
        unSubscribeToUpdates();
        RelayrSdk.logOut();
        invalidateOptionsMenu();
        updateUiForANonLoggedInUser();

        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
    }

    private void updateUiForANonLoggedInUser() {
        mTextView.setText("Relayr");
    }

    private void updateUiForALoggedInUser() {
        loadUserInfo();
    }

    private void loadUserInfo() {
        mUserInfoSubscription = RelayrSdk.getUser()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<User>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(final User user) {
                        Log.i("HTH", "Loaded user: " + user.getName());
                        mTextView.setText("Hello " + user.getName());

                        try {
                            loadDeviceByModelId(user, RelayrSdk.getDeviceModelsCache().getModelByName("Bosh Oven").getId());
                        } catch (DeviceModelsCacheException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    private void loadDeviceByModelId(User user, final String modelId) {
        Log.i("HTH", "Loading devices...");

        user.getDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<Device>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(List<Device> devices) {
                        for (Device device : devices) {
                            if (device.getModelId().equals(modelId)) {
                                Log.i("HTH", "Found device: " + device.getName());

                                subscribeForUpdates(device.toTransmitterDevice());
                                break;
                            }
                        }
                    }
                });
    }

    private void subscribeForUpdates(final TransmitterDevice transmitterDevice) {
        mDevice = transmitterDevice;
        mTextView.setText("Waiting for readings...");

        RelayrSdk.getWebSocketClient()
                .subscribe(transmitterDevice.getId())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Object>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        mTextView.setText("Readings failed.");
                        Log.e("HTH", "Readings failed!");
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Object o) {
                        final Reading reading = new Gson().fromJson(o.toString(), Reading.class);
                        mTextView.setText("path:" + reading.path +
                                "\nmeaning: " + reading.meaning +
                                "\ndata: " + reading.value);
                    }
                });
    }

    private void sendDeviceCommand(final String deviceId, final String modelId) throws DeviceModelsException {
        Log.i("HTH", "Loading model for deviceId: " + deviceId);
        DeviceModel model = RelayrSdk.getDeviceModelsCache().getModelById(modelId);

        Log.i("HTH", "Device model loaded.");

        //get definition of first command for the Bosch oven
        DeviceCommand deviceCommand = model.getLatestFirmware().getDefaultTransport().getCommands().get(1);
        Log.i("HTH", "Device model contains " + model.getLatestFirmware().getDefaultTransport().getCommands().size() + " commands.");

        //create device command
        final Command command = new Command(deviceCommand.getPath(), deviceCommand.getName(), deviceCommand.getValueSchema().asInteger().getMin());
        //send command to device
        sendCommand(deviceId, command);
    }

    /**
     * Sends a command to a device (see listDeviceModels() for list of commands)
     */
    private void sendCommand(String deviceId, final Command command) {
        mTextView.setText("Sending command " + command.toString());

        RelayrSdk.getRelayrApi()
                .sendCommand(deviceId, command)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Void>() {
                    @Override
                    public void onCompleted() {
                        mTextView.setText("Command sent.");
                    }

                    @Override
                    public void onError(Throwable e) {
                        mTextView.setText("Command NOT sent.");
                        Log.e("HTH", "Command '" + command.toString() + "' not sent.");
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        mTextView.setText("Command sent.");
                        Log.i("HTH", "Command '" + command.toString() + "' sent successfully.");
                    }
                });
    }

    private static boolean isSubscribed(Subscription subscription) {
        return subscription != null && !subscription.isUnsubscribed();
    }

    private void unSubscribeToUpdates() {
        if (isSubscribed(mUserInfoSubscription))
            mUserInfoSubscription.unsubscribe();

        if (mDevice != null)
            RelayrSdk.getWebSocketClient().unSubscribe(mDevice.getId());
    }
}
