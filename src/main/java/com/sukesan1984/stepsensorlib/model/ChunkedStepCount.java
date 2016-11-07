package com.sukesan1984.stepsensorlib.model;

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
}
