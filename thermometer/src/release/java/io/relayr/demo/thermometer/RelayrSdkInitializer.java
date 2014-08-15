package io.relayr.demo.thermometer;

import android.content.Context;

import io.relayr.RelayrSdk;

public class RelayrSdkInitializer {

    static void initSdk(Context context) {
        RelayrSdk.init(context);
    }

}
