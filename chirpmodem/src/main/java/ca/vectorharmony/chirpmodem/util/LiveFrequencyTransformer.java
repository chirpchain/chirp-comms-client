package ca.vectorharmony.chirpmodem.util;

import java.security.InvalidParameterException;
import java.util.ArrayList;

/**
 * Created by jlunder on 6/29/15.
 */
public class LiveFrequencyTransformer extends FrequencyTransformer {
    private static final int MAX_PENDING_SAMPLES = (int) SAMPLE_RATE * 6;

    private static float[][][] motherWavelets;

    private float[] bins = new float[BINS_PER_ROW * TOTAL_ROWS];
    private int firstBinRow = 0;
    private int lastBinRow = 0;
    private ArrayList<float[]> sampleBuffer = new ArrayList<float[]>();
    private int consumedSamples = 0;
    private int pendingSamples = 0;

    private int rowsConsumed = 0;

    private boolean adaptiveNoiseReject = true;
    private boolean noiseFloorInited = false;
    private float[] noiseFloor = new float[BINS_PER_ROW];
    private float avgDev = 0.1f;

    public float getTime() {
        return rowsConsumed * ROW_TIME;
    }

    public int getTimeInRows() {
        return rowsConsumed;
    }

    public int getQueuedRows() {
        return (pendingSamples - consumedSamples - WAVELET_WINDOW_SAMPLES + ROW_SAMPLES) / ROW_SAMPLES + getAvailableRows();
    }

    public int getAvailableRows() {
        return (lastBinRow + TOTAL_ROWS - firstBinRow) % TOTAL_ROWS;
    }

    public LiveFrequencyTransformer(boolean adaptiveNoiseReject, boolean zeroNoiseFloor)
    {
        makeMotherWavelets();

        float nf = (float)Math.log(1e-2f);
        this.adaptiveNoiseReject = adaptiveNoiseReject;
        if(!adaptiveNoiseReject || zeroNoiseFloor) {
            for(int i = 0; i < noiseFloor.length; ++i) {
                noiseFloor[i] = nf;
            }
            noiseFloorInited = true;
        }
        avgDev = (float)Math.log(0.707f) - nf;
    }

    public static void makeMotherWavelets() {
        if(motherWavelets == null) {
            synchronized (LiveFrequencyTransformer.class) {
                float[] waveletWindow = new float[WAVELET_WINDOW_SAMPLES];
                double windowK = 2d * Math.PI / WAVELET_WINDOW_SAMPLES;
                for (int i = 0; i < waveletWindow.length; ++i) {
                    waveletWindow[i] = (float) (0.5d + 0.5d * Math.cos(windowK * (i - WAVELET_WINDOW_SAMPLES * 0.5f)));
                }

                float[][][] tempMotherWavelets = new float[BINS_PER_ROW][2][WAVELET_WINDOW_SAMPLES];
                for (int j = 0; j < BINS_PER_ROW; ++j) {
                    float f = MIN_FREQUENCY + ((float) j / (BINS_PER_ROW - 1)) * (MAX_FREQUENCY - MIN_FREQUENCY);
                    for (int i = 0; i < WAVELET_WINDOW_SAMPLES; ++i) {
                        double phase = 2d * Math.PI * f * i / SAMPLE_RATE;
                        tempMotherWavelets[j][0][i] = waveletWindow[i] * (float) Math.sin(phase) * 4f / WAVELET_WINDOW_SAMPLES;
                        tempMotherWavelets[j][1][i] = waveletWindow[i] * (float) Math.cos(phase) * 4f / WAVELET_WINDOW_SAMPLES;
                    }
                }

                motherWavelets = tempMotherWavelets;
            }
        }
    }

    public void warmup(float[] samples) {
        addSamples(samples);
        while(getAvailableRows() > 0) {
            consumeRows(getAvailableRows());
        }
        reset();
    }

    public void addSamples(float[] samples) {
        if(samples.length > 0) {
            if (pendingSamples + samples.length < MAX_PENDING_SAMPLES) {
                sampleBuffer.add(samples);
                pendingSamples += samples.length;
            }
            else {
                System.err.println("Too many samples!!! Maybe they're not being consumed??");
            }
            tryConsumeSamples();
        }
    }

    public void getRows(float[] dest, int offset, int numRows) {
        if(numRows > getAvailableRows()) {
            throw new InvalidParameterException("numRows exceeds getAvailableRows()");
        }
        if(firstBinRow + numRows <= TOTAL_ROWS) {
            System.arraycopy(bins, firstBinRow * BINS_PER_ROW, dest, offset, numRows * BINS_PER_ROW);
        }
        else {
            System.arraycopy(bins, firstBinRow * BINS_PER_ROW, dest, offset,
                    (TOTAL_ROWS - firstBinRow) * BINS_PER_ROW);
            System.arraycopy(bins, 0, dest, offset + (TOTAL_ROWS - firstBinRow) * BINS_PER_ROW,
                    (firstBinRow + numRows - TOTAL_ROWS) * BINS_PER_ROW);
        }
    }

    public void consumeRows(int numRows) {
        if (numRows == 0) return;
        if(numRows > getAvailableRows()) {
            throw new InvalidParameterException("numRows exceeds getAvailableRows()");
        }
        firstBinRow = (firstBinRow + numRows) % TOTAL_ROWS;
        rowsConsumed += numRows;
        tryConsumeSamples();
    }

    private void tryConsumeSamples()
    {
        float[] blockSamples = new float[WAVELET_WINDOW_SAMPLES];
        while(((pendingSamples - consumedSamples) >= WAVELET_WINDOW_SAMPLES) && (getAvailableRows() < (TOTAL_ROWS - 1))) {
            makeContiguousSampleBlock(blockSamples);
            generateOneRowFromContiguousSampleBlock(blockSamples);
            lastBinRow = (lastBinRow + 1) % TOTAL_ROWS;
            consumedSamples += ROW_SAMPLES;
            flushConsumedSamples();
        }
    }

    private void makeContiguousSampleBlock(float[] blockSamples) {
        int i = 0, j = 0;
        float[] thisSeries;
        int thisSeriesOffset = consumedSamples;

        while(i < blockSamples.length) {
            assert j < sampleBuffer.size();
            thisSeries = sampleBuffer.get(j);
            int samplesToCopy = Math.min(thisSeries.length - thisSeriesOffset, blockSamples.length - i);
            System.arraycopy(thisSeries, thisSeriesOffset, blockSamples, i, samplesToCopy);
            i += samplesToCopy;
            ++j;
            thisSeriesOffset = 0;
        }
    }

    private float[] rowScratch = new float[BINS_PER_ROW];

    private void generateOneRowFromContiguousSampleBlock(float[] blockSamples) {
        int offset = lastBinRow * BINS_PER_ROW;

        for(int j = 0; j < BINS_PER_ROW; ++j) {
            float sinSum = 0f;
            float cosSum = 0f;
            for (int i = 0; i < WAVELET_WINDOW_SAMPLES; ++i) {
                sinSum += blockSamples[i] * motherWavelets[j][0][i];
                cosSum += blockSamples[i] * motherWavelets[j][1][i];
            }
            float val = (float) Math.sqrt(sinSum * sinSum + cosSum * cosSum);
            if(val < MIN_AMPLITUDE) {
                rowScratch[j] = LOG_MIN_AMPLITUDE;
            }
            else {
                rowScratch[j] = (float) Math.log(val);
            }
            bins[offset + j] = (rowScratch[j] - noiseFloor[j]) / (avgDev * 4);
        }
        if(adaptiveNoiseReject) {
            if(!noiseFloorInited) {
                float rowStdDev = 0f;
                float rowAvg = 0f;

                for(int j = 0; j < BINS_PER_ROW; ++j) {
                    rowAvg += rowScratch[j];
                }
                rowAvg /= BINS_PER_ROW;
                for(int j = 0; j < BINS_PER_ROW; ++j) {
                    float diff = (rowScratch[j] - rowAvg);
                    rowStdDev += diff * diff;
                }
                rowStdDev = (float)Math.sqrt(rowStdDev / BINS_PER_ROW);
                for(int j = 0; j < BINS_PER_ROW; ++j) {
                    noiseFloor[j] = rowAvg;
                }
                avgDev = rowStdDev;
                noiseFloorInited = true;
            }
            else {
                float rowStdDev = 0f;
                for (int j = 0; j < BINS_PER_ROW; ++j) {
                    float diff = rowScratch[j] - noiseFloor[j];
                    rowStdDev += diff * diff;
                }
                rowStdDev = (float) Math.sqrt(rowStdDev / BINS_PER_ROW);
                for (int j = 0; j < BINS_PER_ROW; ++j) {
                    noiseFloor[j] = noiseFloor[j] * (1f - 1f / 128f) + rowScratch[j] * (1f / 128f);
                }
                avgDev = avgDev * (1f - 1f / 128f) + rowStdDev * 1f / 128f;
            }
        }
    }

    private void flushConsumedSamples() {
        while(!sampleBuffer.isEmpty()) {
            float[] firstSeries = sampleBuffer.get(0);
            if (consumedSamples < firstSeries.length) {
                break;
            }
            consumedSamples -= firstSeries.length;
            pendingSamples -= firstSeries.length;
            sampleBuffer.remove(0);
        }
    }

    public void reset() {
        firstBinRow = lastBinRow = 0;
        pendingSamples = consumedSamples = 0;
        rowsConsumed = 0;
        sampleBuffer.clear();
    }
}
