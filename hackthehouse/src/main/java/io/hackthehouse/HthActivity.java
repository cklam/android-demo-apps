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

import io.relayr.LoginEventListener;
import io.relayr.RelayrSdk;
import io.relayr.model.Command;
import io.relayr.model.Device;
import io.relayr.model.DeviceModel;
import io.relayr.model.Model;
import io.relayr.model.ModelCommand;
import io.relayr.model.ModelReading;
import io.relayr.model.Reading;
import io.relayr.model.TransmitterDevice;
import io.relayr.model.User;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class HthActivity extends Activity implements LoginEventListener {

    private TextView mTextView;
    private Subscription mUserInfoSubscription;
    private TransmitterDevice mDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = View.inflate(this, R.layout.activity_main, null);

        mTextView = (TextView) view.findViewById(R.id.txt);

        setContentView(view);

        if (!RelayrSdk.isUserLoggedIn()) RelayrSdk.logIn(this, this);
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
            RelayrSdk.logIn(this, this);
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

    @Override
    public void onSuccessUserLogIn() {
        Toast.makeText(this, "Logged in", Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }

    @Override
    public void onErrorLogin(Throwable e) {
        Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
        updateUiForANonLoggedInUser();
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
        //Use to list all available device models
        listDeviceModels();
        loadUserInfo();
    }

    private void loadUserInfo() {
        mUserInfoSubscription = RelayrSdk.getRelayrApi()
                .getUserInfo()
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

                        loadDeviceByModelId(user.id, DeviceModel.BOSCH_OVEN.getId());
                    }
                });
    }

    private void loadDeviceByModelId(String userId, final String modelId) {
        Log.i("HTH", "Loading devices...");

        RelayrSdk.getRelayrApi()
                .getUserDevices(userId)
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
                            if (device.getModel().getId().equals(modelId)) {
                                mTextView.setText("Device found.");
                                Log.i("HTH", "Found device: " + device.getName());

                                sendDeviceCommand(device.id, modelId);
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
        Log.i("HTH", "Waiting for readings...");

        RelayrSdk.getWebSocketClient()
                .subscribe(transmitterDevice)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Object>() {
                    @Override
                    public void onCompleted() {
                        mTextView.setText("Readings complete.");
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
                        for (Reading.Data data : reading.readings) {
                            Log.i("HTH", "p: " + data.path + " - m: " + data.meaning + " - d: " + data.value);
                            
                            mTextView.setText("path:" + data.path + "\nmeaning: " +
                                    data.meaning + "\ndata: " + data.value);
                        }
                    }
                });
    }

    private void sendDeviceCommand(final String deviceId, final String modelId) {
        Log.i("HTH", "Loading model for deviceId: " + deviceId);
        RelayrSdk.getRelayrApi()
                .getDeviceModel(modelId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Model>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e("HTH", "Load device model failed.");
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Model model) {
                        Log.i("HTH", "Device model loaded.");

                        //get definition of first command for the Bosch oven
                        final ModelCommand modelCommand = model.getCommands().get(1);
                        Log.i("HTH", "Device model contains " + model.getCommands().size() + " commands.");

                        //create device command
                        final Command command = new Command(modelCommand.path, modelCommand.command, modelCommand.minimum);
                        //send command to device
                        sendCommand(deviceId, command);
                    }
                });
    }

    /**
     * Sends a command to a device (see listDeviceModels() for list of commands)
     */
    private void sendCommand(String deviceId, final Command command) {
        mTextView.setText("Sending command " + command.command);
        Log.i("HTH", "Sending command " + command.command);

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
                        Log.e("HTH", "Command '" + command.command + "' not sent.");
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        mTextView.setText("Command sent.");
                        Log.i("HTH", "Command '" + command.command + "' sent successfully.");
                    }
                });
    }

    /**
     * Lists all supported device models with available readings and commands.
     */
    private void listDeviceModels() {
        Log.i("HTH", "Listing device models...");

        RelayrSdk.getRelayrApi()
                .getDeviceModels()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<Model>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(List<Model> models) {
                        for (Model model : models) {
                            for (ModelReading modelReading : model.getReadings()) {
                                Log.d(model.getName(), modelReading.toString());
                            }
                            for (ModelCommand modelCommand : model.getCommands()) {
                                Log.d(model.getName(), modelCommand.toString());
                            }
                        }
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
            RelayrSdk.getWebSocketClient().unSubscribe(mDevice.id);
    }
}
