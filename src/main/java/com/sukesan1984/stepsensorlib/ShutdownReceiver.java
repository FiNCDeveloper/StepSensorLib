package com.sukesan1984.stepsensorlib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.sukesan1984.stepsensorlib.util.Logger;
import com.sukesan1984.stepsensorlib.util.SensorListener;

/**
 * Created by kosuketakami on 2016/11/05.
 */

public class ShutdownReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (BuildConfig.DEBUG) Logger.log("shutting down");

        context.startService(new Intent(context, SensorListener.class));

        // if the user used a root script for shutdown, the DEVICE_SHUTDOWN
        // broadcast might not be send. Thereforee, the app will check this
        // setting on the next boot and displays an error message if it's not
        // set to true
        context.getSharedPreferences("pedometer", Context.MODE_PRIVATE).edit()
                .putBoolean("correctShutdown", true).commit();

        Database db = Database.getInstance(context);

        db.resetLastUpdatedSteps(db.getLastUpdatedSteps());
        if (BuildConfig.DEBUG) db.logState();

        db.close();
    }
}
