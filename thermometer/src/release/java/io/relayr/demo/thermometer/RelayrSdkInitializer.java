package io.relayr.demo.thermometer;

import android.content.Context;

import io.relayr.android.RelayrSdk;

public class RelayrSdkInitializer {

    static void initSdk(Context context) {
        new RelayrSdk.Builder(context).build();    }

}
