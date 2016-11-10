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
    private final static String COLUMN_DATE_AND_HOUR = "date_and_hour";
    private final static String COLUMN_STEPS = "steps";
    private final static String COLUMN_IS_RECORDED_ON_SERVER = "is_recorded_on_server";
    private final static String COLUMN_LAST_UPDATED = "last_updated";
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
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_DATE_AND_HOUR + " INTEGER UNIQUE, " +
                COLUMN_STEPS + " INTEGER, " +
                COLUMN_IS_RECORDED_ON_SERVER + " INTEGER default 0, " +
                COLUMN_LAST_UPDATED + " INTEGER);");
    }

    /**
     * delete all
     */
    public void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, "", new String[]{});
        db.close();
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
        Cursor c = getByDateAndHour(dateAndHour);
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
     * replace steps count if steps is larger than current databace value.
     *
     * @param dateAndHour
     * @param steps
     */
    public void insertOrReplaceSteps(final long dateAndHour, final int steps) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = getByDateAndHour(dateAndHour);
        db.beginTransaction();
        try {
            if (c.getCount() == 0) {
                if (steps > 0) {
                    insertNewDateAndHour(db, dateAndHour, steps);
                } else {
                    return;
                }
            } else if (c.moveToFirst() && c.getInt(0) < steps) {
                Logger.log("#### update");
                Logger.log("#### data_and_hour: " + dateAndHour);
                Logger.log("#### steps: " + steps);
                ContentValues values = new ContentValues();
                values.put(COLUMN_DATE_AND_HOUR, dateAndHour);
                values.put(COLUMN_STEPS, steps);
                values.put(COLUMN_LAST_UPDATED, DateUtils.getCurrentTimeMllis());
                db.update(TABLE_NAME, values,
                        COLUMN_DATE_AND_HOUR + " = ?",
                        new String[]{String.valueOf(dateAndHour)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

    }

    /**
     * Inserts a new entry in the database, if there is no entry for the given
     * dateAndHour yet.
     * <p/>
     *
     * @param dateAndHour the dateAndHour in ms since 1970
     * @param steps       the current step value
     */

    private void insertNewDateAndHour(SQLiteDatabase db, final long dateAndHour, final int steps) {
        Logger.log("insert new date and hour");
        Logger.log("date_and_hour: " + dateAndHour);
        Logger.log("steps: " + steps);
        ContentValues values = new ContentValues();
        values.put(COLUMN_DATE_AND_HOUR, dateAndHour);
        // use the negative steps as offset
        values.put(COLUMN_STEPS, steps);
        values.put(COLUMN_LAST_UPDATED, DateUtils.getCurrentTimeMllis());
        db.insert(TABLE_NAME, null, values);
        if (BuildConfig.DEBUG) {
            Logger.log("insertDayAndHour" + dateAndHour + " / " + steps);
            logState();
        }
    }

    private Cursor getByDateAndHour(long dateAndHour) {
        return getReadableDatabase().query(TABLE_NAME, new String[]{COLUMN_STEPS},
                COLUMN_DATE_AND_HOUR + " = ?",
                new String[]{String.valueOf(dateAndHour)}, null, null, null);
    }

    private void addToLastEntry(SQLiteDatabase db, int steps) {
        if (steps > 0) {
            db.execSQL("UPDATE " + TABLE_NAME + " SET " + COLUMN_STEPS + " = " + COLUMN_STEPS + " + " + steps
                    + ", " + COLUMN_IS_RECORDED_ON_SERVER + " = 0, " + COLUMN_LAST_UPDATED + " =" + DateUtils.getCurrentTimeMllis() +
                    " WHERE " + COLUMN_DATE_AND_HOUR + "= (SELECT MAX(" + COLUMN_DATE_AND_HOUR + ") FROM " + TABLE_NAME + ");");
        }
    }

    /**
     * Writes the current steps database to the log.
     */
    public void logState() {
        if (BuildConfig.DEBUG) {
            Cursor c = getReadableDatabase()
                    .query(TABLE_NAME, null, null, null, null, null, COLUMN_DATE_AND_HOUR + " DESC", null);
            Logger.log(c);
            c.close();
        }
    }

    /**
     * @param dateAndHour
     * @return
     */
    public int getSteps(final long dateAndHour) {
        Cursor c = getByDateAndHour(dateAndHour);

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
                .query(TABLE_NAME, new String[]{"SUM(" + COLUMN_STEPS + ")"},
                        COLUMN_DATE_AND_HOUR + " >= ? AND " +
                                COLUMN_DATE_AND_HOUR + " <= ?",
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

    public List<ChunkedStepCount> getChunkStepsFrom(final long start) {
        Cursor c = getReadableDatabase()
                .query(TABLE_NAME, new String[]{COLUMN_DATE_AND_HOUR, COLUMN_STEPS},
                        COLUMN_DATE_AND_HOUR + " >= ?", new String[]{String.valueOf(start)}, null, null, null);
        return createChunkedStepCounts(c);
    }

    /**
     * Removes all entries with negative values.
     * <p/>
     * Only call this directly after boot, otherwise it might remove the current
     * day as the current offset is likely to be negative.
     */
    void removeNegativeEntries() {
        getWritableDatabase().delete(TABLE_NAME, COLUMN_STEPS + " < ?", new String[]{"0"});
    }

    /**
     * Remove invalid entries from the database.
     * Currently, an invalid input is such with steps >= 200,000
     */
    public void removeInvalidEntries() {
        getWritableDatabase().delete(TABLE_NAME, COLUMN_STEPS + " >= ?", new String[]{"200000"});
    }

    /**
     * @param stepsSinceBoot steps after boot.
     */
    public void resetLastUpdatedSteps(final int stepsSinceBoot) {
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

    private void initializeLastUpdatedSteps(SQLiteDatabase db, final int stepsSinceBoot) {
        // initialize if there is no date
        if (getSteps(-1) == Integer.MIN_VALUE) {
            saveLastUpdatedSteps(db, stepsSinceBoot);
        }
    }

    private void saveLastUpdatedSteps(SQLiteDatabase db, final int steps) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_STEPS, steps);
        if (db.update(TABLE_NAME, values, COLUMN_DATE_AND_HOUR + " = -1", null) == 0) {
            values.put(COLUMN_DATE_AND_HOUR, -1);
            values.put(COLUMN_LAST_UPDATED, DateUtils.getCurrentTimeMllis());
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
        Cursor c = getReadableDatabase()
                .query(TABLE_NAME, new String[]{COLUMN_DATE_AND_HOUR, COLUMN_STEPS},
                        COLUMN_DATE_AND_HOUR + " != ? and " +
                                COLUMN_IS_RECORDED_ON_SERVER + " = ?", new String[]{"-1", "0"}, null, null, null);
        Logger.log("Not recoreded Chunk Size: " + c.getCount());
        return createChunkedStepCounts(c);
    }

    private List<ChunkedStepCount> createChunkedStepCounts(Cursor c) {
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
        values.put(COLUMN_IS_RECORDED_ON_SERVER, "1");
        int length = dateAndHours.length;
        String args = "";
        String[] dateAndHoursString = new String[length];
        for (int i = 0; i < length; i++) {
            if (i == 0) {
                args = "?";
            } else {
                args += ", ?";
            }
        }
        SQLiteDatabase db = getWritableDatabase();
        db.update(TABLE_NAME, values, String.format(COLUMN_DATE_AND_HOUR + " in (%s)", args), dateAndHoursString);

        Logger.log("update to Recorded: " + dateAndHoursString.toString());
        db.close();
        return;
    }
}
