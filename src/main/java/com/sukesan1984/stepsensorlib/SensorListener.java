package com.sukesan1984.stepsensorlib;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.sukesan1984.stepsensorlib.util.Logger;

public class SensorListener extends Service implements SensorEventListener {

    private final static int MICROSECONDS_IN_ONE_MINUTE = 60 * 1000 * 1000;
    private final static String EXTRA_RESET_DATA = BuildConfig.APPLICATION_ID + ".ResetData";

    /**
     * Start service, and write unsaved data to DB.
     */
    static Intent createIntent(@NonNull Context context) {
        return new Intent(context, SensorListener.class);
    }

    public static Intent createIntentForReset(Context context) {
        return new Intent(context, SensorListener.class).putExtra(EXTRA_RESET_DATA, true);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nobody knows what happens here: step value might magically decrease
        // when this method is called...
        Logger.log(sensor.getName() + " accuracy changed: " + accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Logger.log("################################## onSensorChanged called ####################################");
        if (event.values[0] > Integer.MAX_VALUE) {
            Logger.log("probably not a real value: " + event.values[0]); return;
        }

        int stepsSinceBoot = (int) event.values[0];
        StepCountCoordinator.getInstance().onStepCounterEvent(this, stepsSinceBoot);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.log("################################## onStartCommand called ####################################");
        if (intent.getBooleanExtra(EXTRA_RESET_DATA, false)) {
            Logger.log("Deleting all data and stopping service.");
            unregisterSensor();
            StepCountCoordinator.getInstance().reset();
            Database.getInstance(this).deleteAll();
            stopSelf();
            return START_NOT_STICKY;
        }

        // restart service every minutes get the current step count
        ((AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, System.currentTimeMillis() + 60 * 1000,
                        PendingIntent.getService(this, 2, createIntent(this), PendingIntent.FLAG_UPDATE_CURRENT));
        StepCountCoordinator.getInstance().saveSteps(this);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.log("SensorListener onCreate");
        registerSensor();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Logger.log("sensor service task removed");

        ((AlarmManager) getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, System.currentTimeMillis() + 500, PendingIntent
                        .getService(this, 3, new Intent(this, SensorListener.class), 0));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.log("SensorListener onDestroy");
        unregisterSensor();
    }

    private void unregisterSensor() {
        try {
            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            sm.unregisterListener(this);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Logger.log(e);
                e.printStackTrace();
            }
        }
    }

    private void registerSensor() {
        Logger.log("register sensor listener");
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Logger.log("step sensors: " + sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size());
        if (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size() < 1) {
            return;
        }
        Logger.log("default: " + sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).getName());

        // enable batching with delay of max 5min
        if (StepSensorFacade.isValidStepSensorDevice(this)) {
            sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                    SensorManager.SENSOR_DELAY_NORMAL, 5 * MICROSECONDS_IN_ONE_MINUTE);
        }
    }
}
