package com.sukesan1984.stepsensorlib.util;

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

import com.sukesan1984.stepsensorlib.BuildConfig;
import com.sukesan1984.stepsensorlib.Database;
import com.sukesan1984.stepsensorlib.PreferenceManager;
import com.sukesan1984.stepsensorlib.StepSensorFacade;

/**
 * Created by kosuketakami on 2016/11/05.
 */

public class SensorListener extends Service implements SensorEventListener {

    public final static String ACTION_PAUSE = "pause";
    private static int steps;

    private static boolean WAIT_FOR_VALID_STEPS = false;

    private final static int MICROSECONDS_IN_ONE_MINUTE = 60000000;

    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, SensorListener.class);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nobody knows what happens here: step value might magically decrease
        // when this method is called...
        if (BuildConfig.DEBUG) {
            Logger.log(sensor.getName() + " accuracy changed: " + accuracy);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Logger.log("################################## onSensorChanged called ####################################");
        if (event.values[0] > Integer.MAX_VALUE) {
            if (BuildConfig.DEBUG) {
                Logger.log("probably not a real value: " + event.values[0]);
            }
        } else {
            steps = (int) event.values[0];
            Logger.log("sensor returned steps is " + steps + " and WAIT_FOR_VALID_STEPS is " + WAIT_FOR_VALID_STEPS);
            if (WAIT_FOR_VALID_STEPS && steps > 0) {
                Logger.log("periodically save");
                WAIT_FOR_VALID_STEPS = false;
                Database db = Database.getInstance(this);
                db.updateOrInsert(DateUtils.getCurrentDateAndHour(), steps);
                reRegisterSensor();
                int difference = steps - PreferenceManager.readStepsSinceBoot(this, steps);
                if (difference > 0) {
                    PreferenceManager.writeStepsSinceBoot(this, steps);
                }
                db.close();
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.log("################################## onStartCommand called ####################################");
        if (intent != null && ACTION_PAUSE.equals(intent.getStringExtra("action"))) {
            if (BuildConfig.DEBUG) {
                Logger.log("onStartCommand action: " + intent.getStringExtra("action"));
            }
            if (steps == 0) {
                Database db = Database.getInstance(this);
                steps = db.getLastUpdatedSteps();
                db.close();
            }
            ((AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE))
                    .cancel(PendingIntent.getService(getApplicationContext(), 2,
                            new Intent(this, SensorListener.class),
                            PendingIntent.FLAG_UPDATE_CURRENT));
            stopSelf();
            return START_NOT_STICKY;
        }

        // restart service every minutes get the current step count
        ((AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, System.currentTimeMillis() + 60 * 1000,
                        PendingIntent.getService(getApplicationContext(), 2, new Intent(this, SensorListener.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));
        WAIT_FOR_VALID_STEPS = true;

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Logger.log("SensorListener onCreate");
        }
        reRegisterSensor();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (BuildConfig.DEBUG) {
            Logger.log("sensor service task removed");
        }

        ((AlarmManager) getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, System.currentTimeMillis() + 500, PendingIntent
                        .getService(this, 3, new Intent(this, SensorListener.class), 0));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (BuildConfig.DEBUG) {
            Logger.log("SensorListener onDestroy");
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
    }

    private void reRegisterSensor() {
        if (BuildConfig.DEBUG) {
            Logger.log("re-register sensor listener");
        }
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        try {
            sm.unregisterListener(this);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Logger.log(e);
                e.printStackTrace();
            }
        }

        if (BuildConfig.DEBUG) {
            Logger.log("step sensors: " + sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size());
            if (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size() < 1) {
                return;
            }
            Logger.log("default: " + sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).getName());
        }

        // enable batching with delay of max 5min
        if (StepSensorFacade.isValidStepSensorDevice(this)) {
            sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                    SensorManager.SENSOR_DELAY_NORMAL, 5 * MICROSECONDS_IN_ONE_MINUTE);
        }
    }
}
