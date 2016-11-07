package com.sukesan1984.stepsensorlib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.sukesan1984.stepsensorlib.util.DateUtils;
import com.sukesan1984.stepsensorlib.util.Logger;
import com.sukesan1984.stepsensorlib.util.SensorListener;

/**
 * Created by kosuketakami on 2016/11/05.
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("pedometer", Context.MODE_PRIVATE);

        Database db = Database.getInstance(context);

        if (!prefs.getBoolean("correctShutdown", false)) {
            if (BuildConfig.DEBUG) {
                Logger.log("Incorrect shutdown");
            }
            int steps = db.getLastUpdatedSteps();
            if (BuildConfig.DEBUG) {
                Logger.log("Trying to recover " + steps + " steps");
            }
            db.updateOrInsert(DateUtils.getCurrentDateAndHour(), steps);
        }
        // last entry might still have a negative step value, so remove that
        // row if that's the case
        db.removeNegativeEntries();
        db.close();
        prefs.edit().remove("correctShutdown").apply();

        context.startService(new Intent(context, SensorListener.class));
    }
}
