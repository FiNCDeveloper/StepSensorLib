/*
 * Copyright 2013 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sukesan1984.stepsensorlib.util;

import android.app.ActivityManager;
import android.content.Context;
import android.database.Cursor;

import com.crashlytics.android.Crashlytics;
import com.sukesan1984.stepsensorlib.BuildConfig;

import java.util.Date;
import java.util.List;

public abstract class Logger {

    private static final Date date = new Date();
    private final static String APP = "Pedometer";

    public static void log(Throwable ex) {
        log(ex.getMessage());
        for (StackTraceElement ste : ex.getStackTrace()) {
            log(ste.toString());
        }
    }


    private static String stackToString(StackTraceElement[] stackTrace) {
        String stack = "Finc-log Stacktrace:";
        for (StackTraceElement stackTraceElement : stackTrace) {
            stack += ",at " + (stackTraceElement.toString());
        }
        return stack;
    }

    public static void logInCrashlytics(Context context){
        Crashlytics.log("Thread id: " + Thread.currentThread().getId());
        Crashlytics.log("Process id: " + android.os.Process.myPid());
        Crashlytics.log("Process name: " + getProcessName(context));
        if (BuildConfig.DEBUG) {
            Crashlytics.log("Stack: " + stackToString(Thread.currentThread().getStackTrace()));
        }
    }


    private static String getProcessName(Context context) {
        int pid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> infos = manager.getRunningAppProcesses();
        if (infos != null) {
            for (ActivityManager.RunningAppProcessInfo processInfo : infos) {
                if (processInfo.pid == pid) {
                    return processInfo.processName;
                }
            }
        }

        return null;
    }

    public static void log(final Cursor c) {
        if (!BuildConfig.DEBUG) return;
        c.moveToFirst();
        String title = "";
        for (int i = 0; i < c.getColumnCount(); i++) {
            title += c.getColumnName(i) + "\t| ";
        }
        log(title);
        while (!c.isAfterLast()) {
            title = "";
            for (int i = 0; i < c.getColumnCount(); i++) {
                final int columnTitleLength = c.getColumnName(i).length();
                int columnValueLength = c.getString(i).length();
                int diffLength = columnTitleLength - columnValueLength > 0
                        ? columnTitleLength - columnValueLength
                        : 0;
                String diff = "";
                for (int diffCount = 0; diffCount < diffLength; diffCount++) {
                    diff += " ";
                }
                title += c.getString(i) + diff + "\t| ";
            }
            log(title);
            c.moveToNext();
        }
    }

    @SuppressWarnings("deprecation")
    public static void log(String msg) {
        if (!BuildConfig.DEBUG) return;
        android.util.Log.d(APP, msg);
        date.setTime(System.currentTimeMillis());
    }

    protected void finalize() throws Throwable {
        super.finalize();
    }
}
