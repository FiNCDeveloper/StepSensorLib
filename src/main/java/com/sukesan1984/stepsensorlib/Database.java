package com.sukesan1984.stepsensorlib;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import com.sukesan1984.stepsensorlib.model.ChunkedStepCount;
import com.sukesan1984.stepsensorlib.util.DateUtils;
import com.sukesan1984.stepsensorlib.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */

public class Database extends SQLiteOpenHelper {
    private final static String TABLE_NAME = "steps";
    private final static int DB_VERSION = 1;

    private static Database instance;
    private static final AtomicInteger openCounter = new AtomicInteger();

    private Database(final Context context) {
        super(context, TABLE_NAME, null, DB_VERSION);
    }

    public static synchronized Database getInstance(final Context c) {
        if (instance == null) {
            instance = new Database(c.getApplicationContext());
        }

        openCounter.incrementAndGet();

        return instance;
    }

    @Override
    public void close() {
        if (openCounter.decrementAndGet() == 0) {
            super.close();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (date_and_hour INTEGER UNIQUE, steps INTEGER, is_recorded_on_server INTEGER default 0, last_updated INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // on upgrade
    }

    /**
     * Query the 'steps' table. Remember to close the cursor!
     *
     * @param columns       the colums
     * @param selection     the selection
     * @param selectionArgs the selction arguments
     * @param groupBy       the group by statement
     * @param having        the having statement
     * @param orderBy       the order by statement
     * @return the cursor
     */
    public Cursor query(final String[] columns, final String selection,
                        final String[] selectionArgs, final String groupBy, final String having,
                        final String orderBy, final String limit) {
        return getReadableDatabase()
                .query(TABLE_NAME, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    }

    /**
     * @param dateAndHour    the dateAndHour in ms since 1970
     * @param stepsSinceBoot the steps since boot
     */
    public void updateOrInsert(long dateAndHour, int stepsSinceBoot) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            updateOrInsertWithoutTransaction(db, dateAndHour, stepsSinceBoot);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateOrInsertWithoutTransaction(SQLiteDatabase db, long dateAndHour, int stepsSinceBoot) {
        Cursor c = getReadableDatabase().query(TABLE_NAME, new String[]{"date_and_hour"},
                "date_and_hour = ?",
                new String[]{String.valueOf(dateAndHour)}, null, null, null);
        initializeLastUpdatedSteps(db, stepsSinceBoot);
        int lastUpdatedSteps = getLastUpdatedSteps();
        if (lastUpdatedSteps > stepsSinceBoot) {
            saveLastUpdatedSteps(db, stepsSinceBoot);
            return;
        }


        int addedSteps = stepsSinceBoot - lastUpdatedSteps;
        if (c.getCount() == 0 && stepsSinceBoot >= 0) {
            insertNewDateAndHour(db, dateAndHour, addedSteps);
        } else {
            addToLastEntry(db, addedSteps);
        }
        saveLastUpdatedSteps(db, stepsSinceBoot);
    }

    /**
     * Inserts a new entry in the database, if there is no entry for the given
     * dateAndHour yet.
     * <p/>
     *
     * @param dateAndHour the dateAndHour in ms since 1970
     * @param steps       the current step value
     */

    private void insertNewDateAndHour(SQLiteDatabase db, long dateAndHour, int steps) {
        ContentValues values = new ContentValues();
        values.put("date_and_hour", dateAndHour);
        // use the negative steps as offset
        values.put("steps", steps);
        values.put("last_updated", DateUtils.getCurrentTimeMllis());
        db.insert(TABLE_NAME, null, values);
        if (BuildConfig.DEBUG) {
            Logger.log("insertDayAndHour" + dateAndHour + " / " + steps);
            logState();
        }
    }

    private void addToLastEntry(SQLiteDatabase db, int steps) {
        if (steps > 0) {
            db.execSQL("UPDATE " + TABLE_NAME + " SET steps = steps + " + steps
                    + ", last_updated =" + DateUtils.getCurrentTimeMllis() + " WHERE date_and_hour = (SELECT MAX(date_and_hour) FROM " + TABLE_NAME + ")");
        }
    }

    /**
     * Writes the current steps database to the log.
     */
    public void logState() {
        if (BuildConfig.DEBUG) {
            Cursor c = getReadableDatabase()
                    .query(TABLE_NAME, null, null, null, null, null, "date_and_hour DESC", null);
            Logger.log(c);
            c.close();
        }
    }

    /**
     * @param dateAndHour
     * @return
     */
    public int getSteps(final long dateAndHour) {
        Cursor c = getReadableDatabase().query(TABLE_NAME, new String[]{"steps"},
                "date_and_hour = ?", new String[]{String.valueOf(dateAndHour)},
                null, null, null);

        c.moveToFirst();
        int steps;
        if (c.getCount() == 0) {
            steps = Integer.MIN_VALUE;
        } else {
            steps = c.getInt(0);
        }
        c.close();
        return steps;
    }

    public int getSteps(final long start, final long end) {
        Cursor c = getReadableDatabase()
                .query(TABLE_NAME, new String[]{"SUM(steps)"},
                        "date_and_hour >= ? AND date_and_hour <= ?",
                        new String[]{String.valueOf(start), String.valueOf(end)}, null, null, null);
        int sumSteps;
        if (c.getCount() == 0) {
            sumSteps = 0;
        } else {
            c.moveToFirst();
            sumSteps = c.getInt(0);
        }
        c.close();
        return sumSteps;
    }

    /**
     * Removes all entries with negative values.
     * <p/>
     * Only call this directly after boot, otherwise it might remove the current
     * day as the current offset is likely to be negative.
     */
    void removeNegativeEntries() {
        getWritableDatabase().delete(TABLE_NAME, "steps < ?", new String[]{"0"});
    }

    /**
     * Remove invalid entries from the database.
     * Currently, an invalid input is such with steps >= 200,000
     */
    public void removeInvalidEntries() {
        getWritableDatabase().delete(TABLE_NAME, "steps >= ?", new String[]{"200000"});
    }

    /**
     * @param stepsSinceBoot steps after boot.
     */
    public void resetLastUpdatedSteps(int stepsSinceBoot) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            updateOrInsertWithoutTransaction(db, DateUtils.getCurrentDateAndHour(), stepsSinceBoot);
            saveLastUpdatedSteps(db, 0);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void initializeLastUpdatedSteps(SQLiteDatabase db, int stepsSinceBoot) {
        // initialize if there is no date
        if (getSteps(-1) == Integer.MIN_VALUE) {
            saveLastUpdatedSteps(db, stepsSinceBoot);
        }
    }

    private void saveLastUpdatedSteps(SQLiteDatabase db, int steps) {
        ContentValues values = new ContentValues();
        values.put("steps", steps);
        if (db.update(TABLE_NAME, values, "date_and_hour = -1", null) == 0) {
            values.put("date_and_hour", -1);
            values.put("last_updated", DateUtils.getCurrentTimeMllis());
            db.insert(TABLE_NAME, null, values);
        }
        if (BuildConfig.DEBUG) {
            Logger.log("saving steps in db: " + steps);
        }
    }

    public int getLastUpdatedSteps() {
        int currentSteps = getSteps(-1);
        return currentSteps == Integer.MIN_VALUE ? 0 : currentSteps;
    }

    public int getTodayStep() {
        return getSteps(DateUtils.getStartOfToday(), DateUtils.getCurrentTimeMllis());
    }

    @NonNull
    public List<ChunkedStepCount> getNotRecordedChunkedStepCounts() {
        Cursor c = getReadableDatabase().query(TABLE_NAME, new String[]{"date_and_hour", "steps"},
                "date_and_hour != ? and is_recorded_on_server = ?", new String[]{"-1", "0"}, null, null, null);

        List<ChunkedStepCount> lists = new ArrayList<>();

        if (c.getCount() == 0) {
            return lists;
        }
        while (c.moveToNext()) {
            lists.add(new ChunkedStepCount(c.getLong(0), c.getInt(1)));
        }
        c.close();
        return lists;
    }

    public void updateToRecorded(long[] dateAndHours) {
        ContentValues values = new ContentValues();
        values.put("is_recorded_on_server", "1");
        int length = dateAndHours.length;
        String args = "";
        String[] dateAndHoursString = new String[length];
        for (int i = 0; i < length; i++) {
            if (i == 0) {
                args = "?";
            } else {
                args += ", ?";
            }
            dateAndHoursString[i] = String.valueOf(dateAndHours[i]);
        }
        SQLiteDatabase db = getWritableDatabase();
        db.update(TABLE_NAME, values, String.format("date_and_hour in (%s)", args), dateAndHoursString);
        db.close();
        return;
    }
}
