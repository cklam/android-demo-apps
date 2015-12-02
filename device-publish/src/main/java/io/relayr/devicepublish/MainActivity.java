package io.relayr.devicepublish;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import io.relayr.android.RelayrSdk;
import io.relayr.java.model.CreateDevice;
import io.relayr.java.model.Device;
import io.relayr.java.model.User;
import io.relayr.java.model.action.Reading;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public class MainActivity extends Activity {

    private TextView mWelcomeTV;
    private TextView mDataSentTV;
    private View mCreateLayout;
    private View mPublishLayout;

    private Button mSendData;
    private Button mCreateDevice;

    private User mUser;
    private Device mPublishDevice;

    private Subscription mUserInfoSubscription = Subscriptions.empty();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCreateLayout = findViewById(R.id.layout_create);
        mPublishLayout = findViewById(R.id.layout_publish);

        mWelcomeTV = (TextView) findViewById(R.id.txt_welcome);
        mDataSentTV = (TextView) findViewById(R.id.txt_data_sent);

        mSendData = (Button) findViewById(R.id.btn_send_data);
        mCreateDevice = (Button) findViewById(R.id.btn_create_device);

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

    @Override
    protected void onPause() {
        super.onPause();
        mUserInfoSubscription.unsubscribe();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RelayrSdk.isUserLoggedIn()) updateUiForALoggedInUser();
        else updateUiForANonLoggedInUser();
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
        mUserInfoSubscription.unsubscribe();
        RelayrSdk.logOut();
        invalidateOptionsMenu();
        Toast.makeText(this, R.string.successfully_logged_out, Toast.LENGTH_SHORT).show();
        updateUiForANonLoggedInUser();
    }

    private void updateUiForANonLoggedInUser() {
        mCreateLayout.setVisibility(View.GONE);
        mPublishLayout.setVisibility(View.GONE);
    }

    private void updateUiForALoggedInUser() {
        mCreateLayout.setVisibility(View.VISIBLE);
        loadUserInfo();
    }

    private void loadUserInfo() {
        mUserInfoSubscription = RelayrSdk.getUser()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<User>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        showToast(R.string.something_went_wrong);
                        e.printStackTrace();
                    }

                    @Override public void onNext(User user) {
                        mUser = user;
                        mWelcomeTV.setText(String.format(getString(R.string.hello), user.getName()));
                        setUpDeviceCreation();
                    }
                });
    }

    private void setUpDeviceCreation() {
        if (mPublishDevice != null) {
            setUpDevicePublish();
            return;
        }

        mCreateDevice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String d = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(System.currentTimeMillis()));

                RelayrSdk.getRelayrApi()
                        .createDevice(new CreateDevice("Dummy device " + d, mUser.getId()))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Subscriber<Device>() {
                            @Override public void onCompleted() {}

                            @Override public void onError(Throwable e) {
                                showToast(R.string.device_creation_failed);
                                e.printStackTrace();
                            }

                            @Override public void onNext(Device device) {
                                showToast(getString(R.string.device_created, device.getName()));
                                mPublishDevice = device;

                                setUpDevicePublish();
                            }
                        });
            }
        });
    }

    private void setUpDevicePublish() {
        mCreateLayout.setVisibility(View.GONE);
        mPublishLayout.setVisibility(View.VISIBLE);

        mSendData.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final int nextRand = new Random().nextInt(1000);
                RelayrSdk.getWebSocketClient()
                        .publish(mPublishDevice.getId(), new Reading(0, 0, "test", "/", nextRand))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Subscriber<Void>() {
                            @Override public void onCompleted() {}

                            @Override public void onError(Throwable e) {
                                e.printStackTrace();
                            }

                            @Override public void onNext(Void aVoid) {
                                mDataSentTV.setText("Number " + nextRand + " sent!");
                            }
                        });
            }
        });
    }

    private void showToast(int stringId) {
        Toast.makeText(this, stringId, Toast.LENGTH_SHORT).show();
    }

    private void showToast(String data) {
        Toast.makeText(this, data, Toast.LENGTH_SHORT).show();
    }
}
