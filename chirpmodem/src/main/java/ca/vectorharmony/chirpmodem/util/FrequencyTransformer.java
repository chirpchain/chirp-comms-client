package ca.vectorharmony.chirpmodem.util;

/**
 * Created by jlunder on 6/10/15.
 */
public abstract class FrequencyTransformer {
    private static final float DESIRED_TIME_WINDOW = 1f;
    private static final float DESIRED_ROW_TIME = 0.005f;
    private static final float DESIRED_WAVELET_WINDOW = DESIRED_ROW_TIME * 2;
    public static final float SAMPLE_RATE = 22050;
    public static final int ROW_SAMPLES = (int)Math.rint(DESIRED_ROW_TIME * SAMPLE_RATE);
    public static final float ROW_TIME = ROW_SAMPLES / SAMPLE_RATE;
    public static final int TOTAL_ROWS = (int) Math.ceil(DESIRED_TIME_WINDOW / ROW_TIME);
    public static final float TIME_WINDOW = TOTAL_ROWS * ROW_TIME;
    public static final float MIN_FREQUENCY = 1000f;
    public static final float MAX_FREQUENCY = 6000f;
    public static final float BIN_BANDWIDTH = 100f;
    public static final int BINS_PER_ROW = (int)((MAX_FREQUENCY - MIN_FREQUENCY) / BIN_BANDWIDTH) + 1;
    public static final int WAVELET_WINDOW_SAMPLES = (int)Math.rint(DESIRED_WAVELET_WINDOW * SAMPLE_RATE);
    public static final float WAVELET_WINDOW = WAVELET_WINDOW_SAMPLES / SAMPLE_RATE;
    public static final float MIN_AMPLITUDE = 1e-5f;
    public static final float LOG_MIN_AMPLITUDE = (float)Math.log(MIN_AMPLITUDE);

    public abstract float getTime();
    public abstract int getTimeInRows();
    public abstract int getQueuedRows();
    public abstract int getAvailableRows();

    public void getRows(float[] dest, int numRows) {
        getRows(dest, 0, numRows);
    }

    public abstract void getRows(float[] dest, int offset, int numRows);
    public abstract void consumeRows(int numRows);
    public abstract void reset();
}

