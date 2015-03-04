package io.relayr.cloud_relay;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.List;

import io.relayr.RelayrSdk;
import io.relayr.model.Command;
import io.relayr.model.Device;
import io.relayr.model.Reading;
import io.relayr.model.User;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;

import static android.view.View.OnClickListener;

public class MainActivity extends Activity {
    
    private static final String BUTTON = "85d9250c-fa73-4021-b4fc-7105571f60ee";
    private static final String SIREN_LIGHT = "f5e7bfd3-d38f-41f6-aebe-019c994c6cb6";
    private static final String NOISE_LEVEL = "04ef536d-7aff-4fbd-b25a-d87e1a3d25f6";
    
    private static final Command TURN_SIREN_ON_COMMAND = new Command("", "down_ch_payload", new int[] {1});
    private static final Command TURN_SIREN_OFF_COMMAND = new Command("", "down_ch_payload", new int[] {0});

    private static final Device mNullableDevice = new Device("", "", null, "", "", "", false);
    
    private Device mButtonDevice = mNullableDevice;
    private Device mSirenLightDevice = mNullableDevice;
    private Device mMicrophoneDevice = mNullableDevice;
    
    private int mNumberOfPushes = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initViews();
        fetchDevices();
    }

    private void initViews() {
        View view = View.inflate(this, R.layout.activity_main, null);
        view.findViewById(R.id.btn_turn_siren_on).setOnClickListener(mTurnSirenOnClickListener);
        view.findViewById(R.id.btn_turn_siren_off).setOnClickListener(mTurnSirenOffClickListener);
        setContentView(view);
    }

    private void fetchDevices() {
        (!RelayrSdk.isUserLoggedIn() ? RelayrSdk.logIn(this): RelayrSdk.getRelayrApi().getUserInfo())
                .flatMap(new Func1<User, Observable<List<Device>>>() {
                    @Override
                    public Observable<List<Device>> call(User user) {
                        return user.getDevices();
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<List<Device>>() {
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
                            if (device.id.equals(BUTTON)) {
                                mButtonDevice = device;
                                subscribeToButtonClickPushes();
                            } else if (device.id.equals(SIREN_LIGHT)) {
                                mSirenLightDevice = device;
                            } else if (device.id.equals(NOISE_LEVEL)) {
                                mMicrophoneDevice = device;
                                subscribeToShouts();
                            }
                        }
                    }
                });
    }

    private void subscribeToButtonClickPushes() {
        Log.d("hugo", "Subscribing to readings");
        mButtonDevice.subscribeToCloudReadings()
                .subscribe(new Subscriber<Reading>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e("hugo", "Error receiving data:" + e.getMessage());
                        Toast.makeText(MainActivity.this, "An error occurred", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Reading reading) {
                        Toast.makeText(MainActivity.this, "The button was clicked!", Toast.LENGTH_SHORT).show();
                        mNumberOfPushes++;
                        if (mNumberOfPushes % 2 == 0)
                            mSirenLightDevice.sendCommand(TURN_SIREN_ON_COMMAND).subscribe();
                        else
                            mSirenLightDevice.sendCommand(TURN_SIREN_OFF_COMMAND).subscribe();
                    }
                });
    }

    private final OnClickListener mTurnSirenOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mNumberOfPushes++;
            mSirenLightDevice.sendCommand(TURN_SIREN_ON_COMMAND).subscribe();
        }
    };

    private final OnClickListener mTurnSirenOffClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mNumberOfPushes++;
            mSirenLightDevice.sendCommand(TURN_SIREN_OFF_COMMAND).subscribe();
        }
    };

    private void subscribeToShouts() {
        mMicrophoneDevice.subscribeToCloudReadings()
                .subscribe(new Subscriber<Reading>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Reading reading) {
                        int level = new Gson().fromJson(reading.value.toString(), Integer.class);
                        if (level > 100) 
                            mSirenLightDevice.sendCommand(TURN_SIREN_ON_COMMAND).subscribe();
                    }
                });
    }
    
}
