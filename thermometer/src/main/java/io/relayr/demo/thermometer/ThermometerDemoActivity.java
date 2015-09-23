package io.relayr.demo.thermometer;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import io.relayr.android.RelayrSdk;
import io.relayr.java.model.User;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;

public class ThermometerDemoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thermometer_demo);

        if (!RelayrSdk.isUserLoggedIn()) {
            logIn();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (RelayrSdk.isUserLoggedIn()) {
            getMenuInflater().inflate(R.menu.thermometer_demo_logged_in, menu);
        } else {
            getMenuInflater().inflate(R.menu.thermometer_demo_not_logged_in, menu);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
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
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(ThermometerDemoActivity.this,
                                R.string.unsuccessfully_logged_in, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(User user) {
                        Toast.makeText(ThermometerDemoActivity.this,
                                R.string.unsuccessfully_logged_in, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void logOut() {
        RelayrSdk.logOut();
        invalidateOptionsMenu();
        Toast.makeText(this, R.string.successfully_logged_out, Toast.LENGTH_SHORT).show();
    }
}
