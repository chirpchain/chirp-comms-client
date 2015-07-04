package ca.vectorharmony.chirpmodem.util;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by jlunder on 6/10/15.
 */
public class CodeLibrary {
    public static class Fingerprint {
        protected static final LiveFrequencyTransformer fingerprintFT = new LiveFrequencyTransformer(false, true);
        protected static final float[] pad = new float[FrequencyTransformer.WAVELET_WINDOW_SAMPLES];

        protected float[] code;
        protected float[] bins;
        protected int[][] pattern;
        protected float mean;

        public float[] getBins() {
            return bins;
        }

        public int[][] getPattern() {
            return pattern;
        }

        public int getMatchRows() {
            return pattern.length;
        }

        public float getMean() {
            return mean;
        }

        public Fingerprint(float[] code) {
            this.code = code;

            int fingerprintRows = code.length / FrequencyTransformer.ROW_SAMPLES;

            synchronized (fingerprintFT) {
                fingerprintFT.reset();
                fingerprintFT.addSamples(code);
                fingerprintFT.addSamples(pad);

                bins = new float[fingerprintRows * FrequencyTransformer.BINS_PER_ROW];
                fingerprintFT.getRows(bins, fingerprintRows);
            }

            mean = Util.mean(bins);
            makePattern();
        }

        private void makePattern() {
            int rows = bins.length / FrequencyTransformer.BINS_PER_ROW;
            pattern = new int[rows][];
            float mx = Util.max(getBins());
            float threshold = mx * 0.5f;
            int[] rowTemp = new int[FrequencyTransformer.BINS_PER_ROW];
            int rowTempUsed;
            for (int j = 0; j < rows; ++j) {
                rowTempUsed = 0;
                for (int i = 0; i < FrequencyTransformer.BINS_PER_ROW; ++i) {
                    int offset = j * FrequencyTransformer.BINS_PER_ROW + i;
                    if (bins[offset] > threshold) {
                        rowTemp[rowTempUsed++] = offset;
                    }
                }
                pattern[j] = new int[rowTempUsed];
                System.arraycopy(rowTemp, 0, pattern[j], 0, rowTempUsed);
            }
        }
    }

    public static int NUM_SYMBOLS = 16;

    protected float[] breakCodeSamples;
    private float[][] codeSamples = new float[NUM_SYMBOLS][];
    protected Fingerprint[] codeFingerprints =  new Fingerprint[NUM_SYMBOLS];
    private float minCodeLength = -1f;
    private float maxCodeLength = -1f;

    public float[] getCodeForSymbol(int symbol) {
        if(symbol < 0) {
            return breakCodeSamples;
        }
        else {
            return codeSamples[symbol];
        }
    }

    public Fingerprint getFingerprintForSymbol(int symbol) {
        return codeFingerprints[symbol];
    }

    public int getMinCodeRows() {
        return (int) Math.floor(getMinCodeLength() / FrequencyTransformer.ROW_TIME);
    }

    public float getMinCodeLength() {
        if(minCodeLength < 0) {
            minCodeLength = getMaxCodeLength();

            for (int i = 0; i < NUM_SYMBOLS; ++i) {
                minCodeLength = Math.min(minCodeLength,
                        (float) codeSamples[i].length / FrequencyTransformer.SAMPLE_RATE);
            }
        }

        return minCodeLength;
    }

    public int getMaxCodeRows() {
        return (int) Math.ceil(getMaxCodeLength() / FrequencyTransformer.ROW_TIME);
    }

    public float getMaxCodeLength() {
        if(maxCodeLength < 0) {
            maxCodeLength = 0f;

            for (int i = 0; i < NUM_SYMBOLS; ++i) {
                maxCodeLength = Math.max(maxCodeLength,
                        (float) codeSamples[i].length / FrequencyTransformer.SAMPLE_RATE);
            }
        }

        return maxCodeLength;
    }

    public void fingerprintLibrary() {
        for (int i = 0; i < CodeLibrary.NUM_SYMBOLS; ++i) {
            codeFingerprints[i] = new Fingerprint(codeSamples[i]);
        }
    }

    private static float[] computeNewEntryCrossMatch(ArrayList<CodeEntry> entries, CodeEntry e) {
        float[] matchQs = new float[entries.size()];
        for(int i = 0; i < entries.size(); ++i) {
            CodeEntry ea = entries.get(i);
            float q = e.getCodeMatchQ(ea);
            matchQs[i] = q;
            if(q > e.highestQ) {
                e.highestQ = q;
            }
            e.totalQ += q;
        }
        e.totalQ /= entries.size();
        return matchQs;
    }

    private static void computeCrossMatch(ArrayList<CodeEntry> entries) {
        for(CodeEntry e: entries) {
            e.highestQ = 0;
            e.totalQ = 0;
        }
        for(int j = 0; j < entries.size(); ++j) {
            CodeEntry ea = entries.get(j);
            for(int i = j + 1; i < entries.size(); ++i) {
                CodeEntry eb = entries.get(i);
                float q = ea.getCodeMatchQ(eb);
                if(q > ea.highestQ) {
                    ea.highestQ = q;
                }
                if(q > eb.highestQ) {
                    eb.highestQ = q;
                }
                ea.totalQ += q;
                eb.totalQ += q;
            }
            ea.totalQ /= (entries.size() - 1);
        }
    }

    private static CodeEntry findMaxHighestQEntry(ArrayList<CodeEntry> entries) {
        CodeEntry hqe = entries.get(0);
        for(CodeEntry e: entries) {
            if(e.highestQ > hqe.highestQ) {
                hqe = e;
            }
        }

        return hqe;
    }

    private static CodeEntry findMaxTotalQEntry(ArrayList<CodeEntry> entries) {
        CodeEntry hqe = entries.get(0);
        for(CodeEntry e: entries) {
            if(e.totalQ > hqe.totalQ) {
                hqe = e;
            }
        }

        return hqe;
    }

    public static CodeLibrary makeChirpCodes() {
        CodeLibrary l = new CodeLibrary();

        l.codeSamples[0] = makeChirpCode(0.005f, 1000f, 100f, 3, new int[] {0, 2, 20, 28, 14, 1, 1, 27, 23, 17, 0, 2, 37, 19, 13});
        l.codeSamples[1] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {0, 2, 5, 0, 19, 1, 2, 32, 10, 15});
        l.codeSamples[2] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {1, 1, 18, 16, 10, 0, 1, 22, 0, 16});
        l.codeSamples[3] = makeChirpCode(0.005f, 1000f, 100f, 3, new int[] {1, 1, 5, 10, 11, 0, 1, 13, 33, 12, 0, 1, 3, 19, 11});
        l.codeSamples[4] = makeChirpCode(0.005f, 1000f, 100f, 3, new int[] {1, 1, 14, 23, 16, 1, 2, 22, 20, 10, 1, 1, 24, 19, 14});
        l.codeSamples[5] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {0, 1, 23, 15, 12, 1, 2, 4, 5, 14});
        l.codeSamples[6] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {1, 1, 0, 18, 18, 1, 1, 13, 17, 15});
        l.codeSamples[7] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {0, 2, 23, 46, 17, 1, 1, 7, 14, 15});
        l.codeSamples[8] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {0, 1, 3, 3, 17, 0, 1, 7, 17, 14});
        l.codeSamples[9] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {0, 2, 9, 11, 10, 0, 1, 9, 33, 19});
        l.codeSamples[10] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {0, 1, 37, 24, 12, 1, 2, 47, 23, 19});
        l.codeSamples[11] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {0, 2, 6, 14, 17, 1, 1, 8, 8, 18});
        l.codeSamples[12] = makeChirpCode(0.005f, 1000f, 100f, 3, new int[] {1, 1, 10, 12, 13, 1, 2, 22, 18, 16, 0, 1, 23, 17, 13});
        l.codeSamples[13] = makeChirpCode(0.005f, 1000f, 100f, 3, new int[] {0, 1, 20, 25, 15, 0, 2, 15, 32, 12, 1, 2, 24, 42, 17});
        l.codeSamples[14] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {0, 2, 24, 47, 16, 0, 1, 8, 1, 19});
        l.codeSamples[15] = makeChirpCode(0.005f, 1000f, 100f, 2, new int[] {1, 1, 12, 7, 12, 1, 1, 5, 8, 16});
        l.fingerprintLibrary();
        l.breakCodeSamples = new float[(int)Math.rint(l.getMaxCodeLength() * 1.5f / FrequencyTransformer.SAMPLE_RATE)];

        return l;
    }

    public static CodeLibrary generateAndOptimizeChirpCodes() {
        Random r = new Random(4);
        ArrayList<CodeEntry> entries = new ArrayList<CodeEntry>();

        /*
        for(int i = 0; i < NUM_SYMBOLS; ++i) {
            entries.add(new CodeEntry(r));
        }
        */
        for(int k = 0; k < 1000; ++k) {
            computeCrossMatch(entries);
            float averageQ = 0f;
            for(CodeEntry ee: entries) {
                averageQ += ee.totalQ / entries.size();
            }

            CodeEntry e = new CodeEntry(r);
            if(e.getSelfMatchQ() < 1.0f) {
                continue;
            }
            int pkZeros = 0;
            float[] peaks = null;//e.fp.getPeaks();
            for(int i = 0; i < peaks.length; ++i) {
                if(peaks[i] == 0f) {
                    ++pkZeros;
                }
            }
            if(pkZeros > (peaks.length * 2 / 10)) {
                continue;
            }

            if(entries.size() >= NUM_SYMBOLS) {
                CodeEntry hqe = findMaxHighestQEntry(entries);
                float[] matchQs = computeNewEntryCrossMatch(entries, e);
                if (e.totalQ >= averageQ || e.highestQ >= hqe.highestQ) {
                    continue;
                }

                // Remove 1/2 of worst match
                System.out.println(String.format("Removing entry: highestQ = %g", hqe.highestQ));
                entries.remove(hqe);
            }
            entries.add(e);
        }

        CodeLibrary l = new CodeLibrary();
        for(int i = 0; i < NUM_SYMBOLS; ++i) {
            CodeEntry e = entries.get(i);
            StringBuilder sb = new StringBuilder();
            for(int j = 0; j < e.params.length; ++j) {
                if(j > 0) {
                    sb.append(", ");
                }
                sb.append(e.params[j]);
            }
            System.out.println(String.format("l.codeSamples[%d] = makeChirpCode(%d, new int[] {%s});", i, e.componentCount, sb.toString()));
            l.codeSamples[i] = entries.get(i).code;
        }

        return l;
    }

    private static class CodeEntry {
        public int componentCount;
        public int[] params;
        public float[] code;
        public Fingerprint fp;
        float[] inputPeaks;
        public float highestQ;
        public float totalQ;
        public long conflicts;
        public long tests;

        public CodeEntry(Random r) {
            float minFreq = 1000f;
            float maxFreq = 6000f;
            float freqScale = 100f;
            int freqDivs = (int)((maxFreq - minFreq) / freqScale) + 1;
            float timeScale = 0.005f;

            componentCount = 2 + r.nextInt(2);
            params = new int[componentCount * 5];
            for (int i = 0; i < componentCount; ++i) {
                params[i * 5 + 0] = r.nextInt(2); // style
                params[i * 5 + 1] = 1 + r.nextInt(2); // repeat
                params[i * 5 + 2] = r.nextInt(freqDivs * 1 / 2); // freqA
                params[i * 5 + 3] = params[i * 5 + 2] + r.nextInt(freqDivs * 1 / 2); // freqB
                /*
                params[i * 5 + 2] = r.nextInt(FrequencyTransformer.BINS_PER_ROW - 10) + 2; // freqA
                params[i * 5 + 3] = params[i * 5 + 3] + 4 + r.nextInt(4); // freqB
                */
                if (r.nextBoolean()) {
                    int t = params[i * 5 + 2];
                    params[i * 5 + 2] = params[i * 5 + 3];
                    params[i * 5 + 3] = t;
                }
                params[i * 5 + 4] = 10 + r.nextInt(10); // dur
            }
            code = makeChirpCode(timeScale, minFreq, freqScale, componentCount, params);
            fp = new Fingerprint(code);
        }

        private float getSelfMatchQ() {
            LiveFrequencyTransformer ft = new LiveFrequencyTransformer(false, true);
            ft.addSamples(new float[FrequencyTransformer.ROW_SAMPLES / 2]);
            ft.addSamples(code);
            float[] offsetInputBins = new float[ft.getAvailableRows() * FrequencyTransformer.BINS_PER_ROW];
            float[] offsetInputPeaks = new float[ft.getAvailableRows()];
            ft.getRows(offsetInputBins, ft.getAvailableRows());
            /*
            PeakListRecognizer.findPeaksInput(offsetInputBins, offsetInputPeaks);
            return PeakListRecognizer.matchQuality(fp.getPeaks(), inputPeaks) *
                    PeakListRecognizer.matchQuality(fp.getPeaks(), offsetInputPeaks);
                    */
            return 0f;
        }

        private float getCodeMatchQ(CodeEntry other) {
            /*
            return Math.max(slidingMatchQ(fp.getPeaks(), other.inputPeaks),
                    slidingMatchQ(other.fp.getPeaks(), inputPeaks));
                    */
            return 0f;
        }

        private static float slidingMatchQ(float[] peaksA, float[] peaksB) {
            float bestQ = 0f;
            float[] testBed = new float[peaksB.length];
            int minOffset, maxOffset;
            if (peaksA.length <= peaksB.length) {
                minOffset = -peaksA.length / 2;
                maxOffset = peaksB.length - peaksA.length / 2;
            } else {
                minOffset = (peaksB.length) / 2 - peaksA.length;
                maxOffset = (peaksB.length + 1) / 2;
            }
            for (int i = minOffset; i <= maxOffset; ++i) {
                int srcPos = 0;
                int destPos = i;
                int size = peaksA.length;

                if (destPos < 0) {
                    srcPos = -destPos;
                    size += destPos;
                    destPos = 0;
                }
                if (destPos + size > peaksB.length) {
                    size = peaksB.length - destPos;
                }

                System.arraycopy(peaksA, srcPos, testBed, destPos, size);
                for (int j = 0; j < destPos; ++j) {
                    testBed[j] = 0f;
                }
                for (int j = destPos + size; j < testBed.length; ++j) {
                    testBed[j] = 0f;
                }

                //bestQ = Math.max(PeakListRecognizer.matchQuality(testBed, peaksB), bestQ);
                bestQ = 0;
            }
            return bestQ;
        }
    }

    private static float[] makeChirpCode(float timeScale, float minFreq, float freqScale, int componentCount, int[] params) {
        float[] code = null;

        for(int i = 0; i < componentCount; ++i) {
            code = Util.join(code, makeChirpCodeComponent(params[i * 5 + 0], params[i * 5 + 1], params[i * 5 + 2] * freqScale + minFreq, params[i * 5 + 3] * freqScale + minFreq, params[i * 5 + 4] * timeScale));
        }
        return code;
    }

    private static float[] makeChirpCodeComponent(int style, int repeat, float freqA, float freqB, float duration) {
        float[] code = null;

        if(style == 0) {
            code = Util.join(code, generateLinearWarble(duration, 0.5f * (freqA + freqB), 0.5 * (freqB - freqA), repeat * 0.25f / duration));
        }
        else {
            for (int i = 0; i < repeat; ++i) {
                code = Util.join(code, generateLinearSweep(duration / repeat, freqA, freqB));
            }
        }

        return code;
    }

    private static AdsrEnvelope makeStandardAdsrEnvelope(float duration) {
        return new AdsrEnvelope(0.001f, 0.8f, 0f, 0.8f, duration - 0.011f, 0.01f);
    }

    private static abstract class PhaseGenerator {
        protected double time = 0d;

        protected double getDPhasePerDT(double t) {
            return 0d;
        }

        protected double getDeltaPhase(double t, double deltaT) {
            double dPhasePerDT = getDPhasePerDT(t + 0.5d * deltaT);
            return dPhasePerDT * deltaT;
        }

        public double nextDeltaPhase(double deltaT) {
            double deltaPhase = getDeltaPhase(time, deltaT);
            time += deltaT;
            return deltaPhase;
        }
    }

    private static class SweepPhaseGenerator extends PhaseGenerator {
        private double startFrequency;
        private double frequencySlope;

        public SweepPhaseGenerator(double duration, double startFrequency, double finishFrequency) {
            this.startFrequency = startFrequency;
            this.frequencySlope = (finishFrequency - startFrequency) / duration;
        }

        protected double getDeltaPhase(double t, double deltaT) {
            return Math.PI * 2d * (startFrequency * deltaT + frequencySlope * (t * deltaT));
        }
    }

    private static class WarblePhaseGenerator extends PhaseGenerator {
        private double centerFrequency;
        private double frequencyRange;
        private double modulationFrequency;

        public WarblePhaseGenerator(double centerFrequency, double frequencyRange, double modulationFrequency) {
            this.centerFrequency = centerFrequency;
            this.frequencyRange = frequencyRange;
            this.modulationFrequency = modulationFrequency;
        }

        protected double getDeltaPhase(double t, double deltaT) {
            double t1 = t + deltaT;
            return Math.PI * 2d * (centerFrequency * deltaT) - (frequencyRange / modulationFrequency) *
                    (Math.cos(Math.PI * 2d * modulationFrequency * t1) -
                            Math.cos(Math.PI * 2d * modulationFrequency * t));
        }
    }

    private static float[] generateLinearSweep(float duration, float startFrequency, float finishFrequency) {
        float[] samples = new float[(int) Math.rint(duration * FrequencyTransformer.SAMPLE_RATE)];
        generateLinearToneSweep(samples, 0f, duration,
                new SweepPhaseGenerator(duration, startFrequency, finishFrequency),
                makeStandardAdsrEnvelope(duration));
        return samples;
    }

    private static float[] generateLinearWarble(float duration, double centerFrequency, double frequencyRange,
                                                     double modulationFrequency) {
        float[] samples = new float[(int) Math.rint(duration * FrequencyTransformer.SAMPLE_RATE)];
        generateLinearToneSweep(samples, 0f, duration,
                new WarblePhaseGenerator(centerFrequency, frequencyRange, modulationFrequency),
                makeStandardAdsrEnvelope(duration));
        return samples;
    }

    private static void generateLinearToneSweep(float[] samples, float startTime, float duration,
                                                PhaseGenerator phaseGenerator, AdsrEnvelope envelope) {
        double deltaT = 1d / FrequencyTransformer.SAMPLE_RATE;
        double phase, t;
        int startSample = (int) Math.ceil(startTime * FrequencyTransformer.SAMPLE_RATE);
        int finishSample = (int) Math.floor((startTime + duration) * FrequencyTransformer.SAMPLE_RATE);

        t = (double) startSample / FrequencyTransformer.SAMPLE_RATE - startTime;
        phase = phaseGenerator.nextDeltaPhase(t);

        for (int i = startSample; i < finishSample; ++i) {
            phase %= 2d * Math.PI;
            float s = (float)Math.sin(phase) * envelope.getEnvelopeValue((float) t);
            //float s = phase > Math.PI ? 1 : -1;
            samples[i] += s;
            phase += phaseGenerator.nextDeltaPhase(deltaT);
            t += deltaT;
        }
    }
}
