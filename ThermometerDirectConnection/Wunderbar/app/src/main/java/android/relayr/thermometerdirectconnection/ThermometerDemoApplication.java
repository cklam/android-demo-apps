package android.relayr.thermometerdirectconnection;

import android.app.Application;

import io.relayr.RelayrSdk;

/**
 * Created by peterdwersteg on 11/4/14.
 */
public class ThermometerDemoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RelayrSdk.init(this);
    }
}
