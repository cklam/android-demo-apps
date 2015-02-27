package io.hackthehouse;

import android.app.Application;

public class HthApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        RelayrSdkInitializer.initSdk(this);
    }

}
