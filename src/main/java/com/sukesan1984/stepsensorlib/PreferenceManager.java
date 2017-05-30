package com.sukesan1984.stepsensorlib;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

// TODO: use this for service restart
class PreferenceManager {
    private static String PEDOMETER = "pedometer";
    private static String CORRECT_SHUTDOWN = "corretctShutdown";
    private static String STEPS_SINCE_BOOT = "stepsSinceBoot";

    public static void writeCorrectShutDown(@NonNull Context context, boolean isCorrectShutdown) {
        SharedPreferences prefs = context.getSharedPreferences(PEDOMETER, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(CORRECT_SHUTDOWN, isCorrectShutdown).commit();
    }

    public static boolean readCorrectShutDown(@NonNull Context context, boolean defaultBoolean) {
        SharedPreferences prefs = context.getSharedPreferences(PEDOMETER, Context.MODE_PRIVATE);
        return prefs.getBoolean(CORRECT_SHUTDOWN, defaultBoolean);
    }

    public static void deleteCorrectShutDown(@NonNull Context context) {
        context.getSharedPreferences(PEDOMETER, Context.MODE_PRIVATE)
                .edit().remove(CORRECT_SHUTDOWN).apply();
    }

    public static int readStepsSinceBoot(@NonNull Context context, int defaultSteps) {
        return context.getSharedPreferences(PEDOMETER, Context.MODE_PRIVATE)
                .getInt(STEPS_SINCE_BOOT, defaultSteps);
    }

    public static void writeStepsSinceBoot(@NonNull Context context, int steps) {
        context.getSharedPreferences(PEDOMETER, Context.MODE_PRIVATE).edit()
                .putInt(STEPS_SINCE_BOOT, steps).commit();
    }

    public static void deleteStepsSinceBoot(@NonNull Context context) {
        context.getSharedPreferences(PEDOMETER, Context.MODE_PRIVATE)
                .edit()
                .remove(STEPS_SINCE_BOOT)
                .apply();

    }
}
