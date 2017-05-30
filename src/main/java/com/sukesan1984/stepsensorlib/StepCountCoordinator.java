package com.sukesan1984.stepsensorlib;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.sukesan1984.stepsensorlib.util.DateUtils;

class StepCountCoordinator {
    private static final String TAG = "StepCountCoordinator";
    private static volatile StepCountCoordinator singleton;

    @Nullable
    private Long dateAndHourOfLastEvent;
    private int stepsOffset;
    private int unsavedSteps = 0;

    public static StepCountCoordinator getInstance() {
        if (singleton == null) {
            synchronized (StepCountCoordinator.class) {
                if (singleton == null) {
                    singleton = new StepCountCoordinator();
                }
            }
        }
        return singleton;
    }

    private StepCountCoordinator() {
    }

    public synchronized void onStepCounterEvent(Context context, int stepsSinceBoot) {
        long dateAndHour = DateUtils.getCurrentDateAndHour();
        if (dateAndHourOfLastEvent == null) {
            dateAndHourOfLastEvent = dateAndHour;
            stepsOffset = stepsSinceBoot;
            Log.v(TAG, "onStepSensorEvent: stepsOffset is set to " + unsavedSteps);
            return;
        }

        if (dateAndHour != dateAndHourOfLastEvent) {
            saveSteps(context);
        }
        unsavedSteps = stepsSinceBoot - stepsOffset;
        Log.v(TAG, "onStepSensorEvent: " + unsavedSteps);
        dateAndHourOfLastEvent = dateAndHour;
    }

    public synchronized void saveSteps(Context context) {
        if (dateAndHourOfLastEvent == null) return;
        Database database = Database.getInstance(context);
        Log.v(TAG, "saveSteps: " + unsavedSteps);
        if (database.addSteps(dateAndHourOfLastEvent, unsavedSteps)) {
            unsavedSteps = 0;
        } else {
            Log.e(TAG, "Failed to save steps.");
        }
        database.close();
    }

    public synchronized int getTodaySteps(Context context) {
        long dateAndHour = DateUtils.getCurrentDateAndHour();
        if (dateAndHourOfLastEvent != null && dateAndHour != dateAndHourOfLastEvent) {
            // Save and flush step count before fetch.
            saveSteps(context);
        }
        return Database.getInstance(context).getTodayStep() + unsavedSteps;
    }
}
