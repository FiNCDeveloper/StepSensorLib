package com.sukesan1984.stepsensorlib;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.sukesan1984.stepsensorlib.model.ChunkStepCount;
import com.sukesan1984.stepsensorlib.util.DateUtils;
import com.sukesan1984.stepsensorlib.util.Logger;
import java.util.ArrayList;
import java.util.List;

class Database extends SQLiteOpenHelper {
    private final static String TABLE_NAME = "steps";
    private final static String COLUMN_DATE_AND_HOUR = "date_and_hour";
    private final static String COLUMN_STEPS = "steps";
    private final static String COLUMN_IS_RECORDED_ON_SERVER = "is_recorded_on_server";
    private final static String COLUMN_LAST_UPDATED = "last_updated";
    private final static int DB_VERSION = 2;

    private static Database instance;

    private Database(final Context context) {
        super(context, TABLE_NAME, null, DB_VERSION);
    }

    public static synchronized Database getInstance(final Context c) {
        if (instance == null) {
            instance = new Database(c.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (db == null) {
            return;
        }
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
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.delete(TABLE_NAME, "", new String[]{});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.delete("steps", "date_and_hour = ?", new String[]{"-1"});
        }
    }

    /**
     * Add step count in table. If no row matching to targetDateAndHour is exist, it will be created.
     *
     * @param targetDateAndHour Key for table.
     * @param stepsToAdd        Count to be added.
     * @return Total count after add, or negative value when failed.
     * @throws IllegalArgumentException if stepsToAdd is negative value.
     */
    public int addSteps(long targetDateAndHour, int stepsToAdd) {
        if (stepsToAdd < 0) throw new IllegalArgumentException("stepsToAdd should not be negative value.");
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            int currentSteps = getStepsImpl(db, targetDateAndHour);
            int newSteps = currentSteps + stepsToAdd;
            insertOrReplaceStepRow(db, targetDateAndHour, newSteps, false);
            db.setTransactionSuccessful();
            return newSteps;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (db != null) {
                db.endTransaction();
            }
        }
    }

    private void insertOrReplaceStepRow(SQLiteDatabase db, long dateAndHour, int steps, @Nullable Boolean markAsRecorded) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_DATE_AND_HOUR, dateAndHour);
        values.put(COLUMN_STEPS, steps);
        values.put(COLUMN_LAST_UPDATED, DateUtils.getCurrentTimeMllis());
        if (markAsRecorded != null) {
            values.put(COLUMN_IS_RECORDED_ON_SERVER, markAsRecorded ? 1 : 0);
        }
        db.replaceOrThrow(TABLE_NAME, null, values);
    }

    public int getSteps(final long dateAndHour) {
        Logger.log("getStep dateAndHour" + dateAndHour);
        SQLiteDatabase db = getReadableDatabase();
        return getStepsImpl(db, dateAndHour);
    }

    private int getStepsImpl(SQLiteDatabase db, long dateAndHour) {
        Cursor c = db.query(TABLE_NAME, new String[]{COLUMN_STEPS},
                COLUMN_DATE_AND_HOUR + " = ?",
                new String[]{String.valueOf(dateAndHour)}, null, null, null);
        try {
            if (c == null) {
                return 0;
            }
            c.moveToFirst();
            if (c.getCount() == 0) {
                return 0;
            }
            return c.getInt(0);
        } finally {
            closeCursor(c);
        }
    }

    public int getSteps(final long start, final long end) {
        Cursor c = null;
        try {
            c = getReadableDatabase()
                    .query(TABLE_NAME, new String[]{"SUM(" + COLUMN_STEPS + ")"},
                            COLUMN_DATE_AND_HOUR + " >= ? AND " +
                                    COLUMN_DATE_AND_HOUR + " <= ?",
                            new String[]{String.valueOf(start), String.valueOf(end)}, null, null, null);
            if (c == null) {
                return 0;
            }
            int sumSteps;
            if (c.getCount() == 0) {
                sumSteps = 0;
            } else {
                c.moveToFirst();
                sumSteps = c.getInt(0);
            }
            return sumSteps;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            closeCursor(c);
        }
    }

    @NonNull
    public List<ChunkStepCount> getChunkStepsSince(final long start) {
        Cursor c = null;
        try {
            c = getReadableDatabase()
                    .query(TABLE_NAME, new String[]{COLUMN_DATE_AND_HOUR, COLUMN_STEPS},
                            COLUMN_DATE_AND_HOUR + " >= ?", new String[]{String.valueOf(start)}, null, null, null);

            if (c == null) {
                return new ArrayList<>();
            }

            return createChunkedStepCounts(c);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            closeCursor(c);
        }
    }

    public int getTodayStep() {
        Logger.log("getTodayStep");
        return getSteps(DateUtils.getStartOfToday(), DateUtils.getCurrentTimeMllis());
    }

    @NonNull
    public List<ChunkStepCount> getNotRecordedChunkStepCounts() {
        Cursor c = null;
        try {
            c = getReadableDatabase()
                    .query(TABLE_NAME, new String[]{COLUMN_DATE_AND_HOUR, COLUMN_STEPS},
                            COLUMN_DATE_AND_HOUR + " != ? and " +
                                    COLUMN_IS_RECORDED_ON_SERVER + " = ?", new String[]{"-1", "0"}, null, null, null);
            Logger.log("Not recoreded Chunk Size: " + c.getCount());
            // cursor close is in method
            return createChunkedStepCounts(c);
        } catch (Exception e) {
            e.printStackTrace();
            if (c != null) {
                c.close();
            }
            return new ArrayList<>();
        }
    }

    private List<ChunkStepCount> createChunkedStepCounts(Cursor c) {
        if (c == null) {
            return null;
        }
        List<ChunkStepCount> lists = new ArrayList<>();

        if (c.getCount() == 0) {
            return lists;
        }
        while (c.moveToNext()) {
            lists.add(new ChunkStepCount(c.getLong(0), c.getInt(1)));
        }
        return lists;
    }

    public void increaseByServerChunkStepCounts(List<ChunkStepCount> chunkStepCounts) {
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            for (ChunkStepCount chunkStepCount : chunkStepCounts) {
                increaseBySeverChunkStepCount(db, chunkStepCount);
            }
            db.setTransactionSuccessful();
        } finally {
            if (db != null) {
                db.endTransaction();
            }
        }
    }

    private void increaseBySeverChunkStepCount(SQLiteDatabase db, ChunkStepCount chunkStepCount) {
        int currentSteps = getStepsImpl(db, chunkStepCount.unixTimeMillis);
        if (chunkStepCount.steps >= currentSteps) {
            insertOrReplaceStepRow(db, chunkStepCount.unixTimeMillis, chunkStepCount.steps, true);
        }
    }

    private void closeCursor(@Nullable Cursor cursor) {
        if (cursor != null) cursor.close();
    }
}
