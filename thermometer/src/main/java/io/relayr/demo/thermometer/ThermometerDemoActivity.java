package io.relayr.demo.thermometer;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.relayr.android.RelayrSdk;
import io.relayr.java.model.Transmitter;
import io.relayr.java.model.TransmitterDevice;
import io.relayr.java.model.User;
import io.relayr.java.model.action.Reading;
import io.relayr.java.model.models.error.DeviceModelsCacheException;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public class ThermometerDemoActivity extends Activity {

    private TextView mWelcomeTextView;
    private TextView mTemperatureValueTextView;
    private TextView mTemperatureNameTextView;
    private TransmitterDevice mDevice;
    private Subscription mUserInfoSubscription = Subscriptions.empty();
    private Subscription mTemperatureDeviceSubscription = Subscriptions.empty();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = View.inflate(this, R.layout.activity_thermometer_demo, null);

        mWelcomeTextView = (TextView) view.findViewById(R.id.txt_welcome);
        mTemperatureValueTextView = (TextView) view.findViewById(R.id.txt_temperature_value);
        mTemperatureNameTextView = (TextView) view.findViewById(R.id.txt_temperature_name);

        setContentView(view);

        if (RelayrSdk.isUserLoggedIn()) {
            updateUiForALoggedInUser();
        } else {
            updateUiForANonLoggedInUser();
            logIn();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (RelayrSdk.isUserLoggedIn()) getMenuInflater().inflate(R.menu.demo_logged_in, menu);
        else getMenuInflater().inflate(R.menu.demo_not_logged_in, menu);

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

    private void logIn() {
        RelayrSdk.logIn(this)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<User>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        showToast(R.string.unsuccessfully_logged_in);
                        updateUiForANonLoggedInUser();
                    }

                    @Override public void onNext(User user) {
                        showToast(R.string.successfully_logged_in);
                        invalidateOptionsMenu();
                        updateUiForALoggedInUser();
                    }
                });
    }

    private void logOut() {
        unSubscribeToUpdates();
        RelayrSdk.logOut();
        invalidateOptionsMenu();
        Toast.makeText(this, R.string.successfully_logged_out, Toast.LENGTH_SHORT).show();
        updateUiForANonLoggedInUser();
    }

    private void updateUiForANonLoggedInUser() {
        mTemperatureValueTextView.setVisibility(View.GONE);
        mTemperatureNameTextView.setVisibility(View.GONE);
        mWelcomeTextView.setText(R.string.hello_relayr);
    }

    private void updateUiForALoggedInUser() {
        mTemperatureValueTextView.setVisibility(View.VISIBLE);
        mTemperatureNameTextView.setVisibility(View.VISIBLE);
        loadUserInfo();
    }

    private void loadUserInfo() {
        mUserInfoSubscription = RelayrSdk.getUser()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<User>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        showToast(R.string.something_went_wrong);
                        e.printStackTrace();
                    }

                    @Override public void onNext(User user) {
                        String hello = String.format(getString(R.string.hello), user.getName());
                        mWelcomeTextView.setText(hello);
                        loadTemperatureDevice(user);
                    }
                });
    }

    private void loadTemperatureDevice(User user) {
        mTemperatureDeviceSubscription = user.getTransmitters()
                .flatMap(new Func1<List<Transmitter>, Observable<List<TransmitterDevice>>>() {
                    @Override
                    public Observable<List<TransmitterDevice>> call(List<Transmitter> transmitters) {
                        // This is a naive implementation. Users may own many WunderBars or other
                        // kinds of transmitter.
                        if (transmitters.isEmpty())
                            return Observable.from(new ArrayList<List<TransmitterDevice>>());
                        return RelayrSdk.getRelayrApi().getTransmitterDevices(transmitters.get(0).getId());
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<List<TransmitterDevice>>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        showToast(R.string.something_went_wrong);
                        e.printStackTrace();
                    }

                    @Override public void onNext(List<TransmitterDevice> devices) {
                        try {
                            String modelId = RelayrSdk.getDeviceModelsCache()
                                    .getModelByName("Wunderbar Thermometer", false).getId();
                            for (TransmitterDevice device : devices) {
                                if (device.getModelId().equals(modelId)) {
                                    subscribeForTemperatureUpdates(device);
                                    return;
                                }
                            }
                        } catch (DeviceModelsCacheException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unSubscribeToUpdates();
    }

    private void unSubscribeToUpdates() {
        mUserInfoSubscription.unsubscribe();
        mTemperatureDeviceSubscription.unsubscribe();
        if (mDevice != null) RelayrSdk.getWebSocketClient().unSubscribe(mDevice.getId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RelayrSdk.isUserLoggedIn()) updateUiForALoggedInUser();
        else updateUiForANonLoggedInUser();
    }

    private void subscribeForTemperatureUpdates(TransmitterDevice device) {
        mDevice = device;
        device.subscribeToCloudReadings()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Reading>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        showToast(R.string.something_went_wrong);
                        e.printStackTrace();
                    }

                    @Override public void onNext(Reading reading) {
                        if (reading.meaning.equals("temperature"))
                            mTemperatureValueTextView.setText(reading.value + "˚C");
                    }
                });
    }

    private void showToast(int stringId) {
        Toast.makeText(ThermometerDemoActivity.this, stringId, Toast.LENGTH_SHORT).show();
    }
}
