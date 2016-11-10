package com.sukesan1984.stepsensorlib.model;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by kosuketakami on 2016/11/07.
 */

public class ChunkedStepCount {
    public long unixTimeMillis;
    public int steps;

    public ChunkedStepCount(long unixTimeMillis, int steps) {
        this.unixTimeMillis = unixTimeMillis;
        this.steps = steps;
    }

    /**
     * get 'yyyy-mm-dd' format
     */
    public String getDayString() {
        Date date = new Date(this.unixTimeMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(date);
    }

    /**
     * @return unixTimeMillis to hour of start.
     */
    public String getHour() {
        Date date = new Date(this.unixTimeMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("HH");
        return sdf.format(date);
    }
}
