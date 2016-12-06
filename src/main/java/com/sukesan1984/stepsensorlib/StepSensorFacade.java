package com.sukesan1984.stepsensorlib;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Step Sensor Facade
 */

public class StepSensorFacade {
    public static boolean isValidStepSensorDevice(Context context) {
        // TODO: get from server side black list model.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER);
    }
}
