package io.relayr.demo.thermometer;

import android.content.Context;

import io.relayr.android.RelayrSdk;

abstract class RelayrSdkInitializer {

    static void initSdk(Context context) {
        new RelayrSdk.Builder(context).inMockMode(true).build();
    }

}
