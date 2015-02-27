package io.hackthehouse;

import android.app.Activity;
import android.os.Bundle;
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
import io.relayr.model.Model;
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
        updateUiForALoggedInUser();
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
                        mTextView.setText("Hello " + user.getName());
                        getModels(user.id);
                    }
                });
    }

    public void getModels(final String userId) {
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
                            if (model.getName().equals("Bosch oven")) {
                                mTextView.setText("Model found");
                                loadDeviceByModelId(userId, model.getId());
                            }
                        }
                    }
                });
    }

    private void loadDeviceByModelId(String userId, final String modelId) {
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
                                mTextView.setText("Device found");
                                subscribeForUpdates(new TransmitterDevice(device.id, device.getSecret(),
                                        device.getOwner(), device.getName(), modelId));
                            }
                        }
                    }
                });
    }

    private void subscribeForUpdates(final TransmitterDevice transmitterDevice) {
        mDevice = transmitterDevice;
        mTextView.setText("Waiting for readings.");

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
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Object o) {
                        final Reading reading = new Gson().fromJson(o.toString(), Reading.class);
                        for (Reading.Data data : reading.readings)
                            mTextView.setText("path:" + data.path + " - meaning: " + 
                                    data.meaning + " - data: " + data.value);
                    }
                });
    }

    private void sendCommand(String deviceId, Command command) {
        RelayrSdk.getRelayrApi()
                .sendCommand(deviceId, command)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Void>() {
                    @Override
                    public void onCompleted() {
                        mTextView.setText("Command send.");
                    }

                    @Override
                    public void onError(Throwable e) {
                        mTextView.setText("Command NOT send.");
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        mTextView.setText("Command send.");
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
