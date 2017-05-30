package com.sukesan1984.stepsensorlib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.sukesan1984.stepsensorlib.util.Logger;

public class ShutdownReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (BuildConfig.DEBUG) Logger.log("shutting down");
        StepCountCoordinator.getInstance().saveSteps(context);
    }
}
