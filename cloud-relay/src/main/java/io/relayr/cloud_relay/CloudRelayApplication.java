package io.relayr.cloud_relay;

import android.app.Application;

import io.relayr.android.RelayrSdk;

public class CloudRelayApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new RelayrSdk.Builder(this).build();
    }

}
