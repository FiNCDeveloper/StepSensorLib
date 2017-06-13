package com.sukesan1984.stepsensorlib;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;
import com.sukesan1984.stepsensorlib.util.DateUtils;

class StepCountCoordinator {
    private static final String TAG = "StepCountCoordinator";
    private static final StepCountCoordinator singleton = new StepCountCoordinator();
    // https://finc.slack.com/archives/C3552EVFV/p1497343956278850
    // https://finc.slack.com/archives/C2AFVMQ5V/p1495508346662505
    private static final long MAX_STEPS_PER_HOUR = 18000;

    @Nullable
    private Long dateAndHourOfLastEvent;
    private int lastSteps;
    private int unsavedSteps = 0;

    public static StepCountCoordinator getInstance() {
        return singleton;
    }

    private StepCountCoordinator() {
    }

    public synchronized void onStepCounterEvent(Context context, int stepsSinceBoot) {
        long dateAndHour = DateUtils.getCurrentDateAndHour();
        if (dateAndHourOfLastEvent == null) {
            dateAndHourOfLastEvent = dateAndHour;
            lastSteps = stepsSinceBoot;
            Log.v(TAG, "onStepCounterEvent: lastSteps is set to " + lastSteps);
            return;
        }

        if (dateAndHour != dateAndHourOfLastEvent) {
            Log.d(TAG, "Save by hour change.");
            saveSteps(context);
        }

        int increment = stepsSinceBoot - lastSteps;
        if (increment > MAX_STEPS_PER_HOUR) {
            Log.e(TAG, "onStepCounterEvent: Skipping steps " + increment + " exceeds limit of " + MAX_STEPS_PER_HOUR + ".");
            lastSteps = stepsSinceBoot;
            return;
        }

        unsavedSteps += increment;
        Log.v(TAG, "onStepCounterEvent: " + unsavedSteps);
        dateAndHourOfLastEvent = dateAndHour;
        lastSteps = stepsSinceBoot;
    }

    public synchronized void saveSteps(Context context) {
        if (dateAndHourOfLastEvent == null) return;
        Log.v(TAG, "saveSteps: " + unsavedSteps);
        if (unsavedSteps == 0) return;
        Database database = Database.getInstance(context);
        int newSteps = database.addSteps(dateAndHourOfLastEvent, unsavedSteps);
        if (newSteps < 0) {
            Log.e(TAG, "Failed to save steps.");
        } else {
            unsavedSteps = 0;
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

    public synchronized void reset() {
        dateAndHourOfLastEvent = null;
        lastSteps = 0;
        unsavedSteps = 0;
    }
}
