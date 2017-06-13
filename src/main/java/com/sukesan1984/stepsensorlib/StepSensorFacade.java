package com.sukesan1984.stepsensorlib;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;

import com.sukesan1984.stepsensorlib.model.ChunkStepCount;

import java.util.ArrayList;
import java.util.List;

/**
 * Step Sensor Facade
 */

public class StepSensorFacade {
    private StepSensorFacade() {
        throw new AssertionError();
    }

    public static boolean isValidStepSensorDevice(Context context) {
        // TODO: get from server side black list model.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER);
    }

    public static void startService(Context context) {
        context.startService(SensorListener.createIntent(context));
    }

    public static void saveNow(Context context) {
        StepCountCoordinator.getInstance().saveSteps(context);
    }

    public static int getTodaySteps(Context context) {
        return StepCountCoordinator.getInstance().getTodaySteps(context);
    }

    public static void clearAllData(Context context) {
        context.startService(SensorListener.createIntentForReset(context));
    }

    public static void increaseByServerChunkStepCounts(Context context, List<ChunkStepCount> chunkStepCounts) {
        saveNow(context);
        Database.getInstance(context).increaseByServerChunkStepCounts(chunkStepCounts);
    }

    @NonNull
    public static List<ChunkStepCount> getChunkStepsSince(Context context, long dateAndHour) {
        saveNow(context);
        return Database.getInstance(context).getChunkStepsSince(dateAndHour);
    }

    @NonNull
    public static List<ChunkStepCount> getNotRecordedChunkStepCounts(Context context) {
        saveNow(context);
        return Database.getInstance(context).getNotRecordedChunkStepCounts();
    }
}
