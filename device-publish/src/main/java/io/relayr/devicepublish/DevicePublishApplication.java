package io.relayr.devicepublish;

import android.app.Application;

import io.relayr.android.RelayrSdk;

public class DevicePublishApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new RelayrSdk.Builder(this).inMockMode(false).build();
    }
}
