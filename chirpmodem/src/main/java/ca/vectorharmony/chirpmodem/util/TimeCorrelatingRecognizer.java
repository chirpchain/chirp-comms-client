package ca.vectorharmony.chirpmodem.util;

import java.util.Random;

/**
 * Created by jlunder on 6/16/15.
 */
public class TimeCorrelatingRecognizer extends CodeRecognizer {
    public static class Parameters {
        private static final float ROW_THRESHOLD_MIN = 0.0001f;
        private static final float ROW_THRESHOLD_MAX = 1f;
        private static final float ROW_THRESHOLD_LOG_MIN = (float)Math.log(ROW_THRESHOLD_MIN);
        private static final float ROW_THRESHOLD_LOG_MAX = (float)Math.log(ROW_THRESHOLD_MAX);
        private static final float BIN_MINIMUM_MIN = -5f;
        private static final float BIN_MINIMUM_MAX = 5f;
        private static final float ZONE_THRESHOLD_MIN = 0.001f;
        private static final float ZONE_THRESHOLD_MAX = 1f;
        private static final float ZONE_THRESHOLD_LOG_MIN = (float)Math.log(ZONE_THRESHOLD_MIN);
        private static final float ZONE_THRESHOLD_LOG_MAX = (float)Math.log(ZONE_THRESHOLD_MAX);
        private static final int ZONE_COUNT_MIN = 1;
        private static final int ZONE_COUNT_MAX = 16;
        private static final float ZONE_COUNT_THRESHOLD_MIN = 0f;
        private static final float ZONE_COUNT_THRESHOLD_MAX = 1f;
        private static final float MATCH_BASE_THRESHOLD_MIN = 0f;
        private static final float MATCH_BASE_THRESHOLD_MAX = 1f;
        private static final float MATCH_BEST_TO_SECOND_BEST_THRESHOLD_MIN = 1e-3f;
        private static final float MATCH_BEST_TO_SECOND_BEST_THRESHOLD_MAX = 1f;
        private static final float MATCH_BEST_TO_SECOND_BEST_THRESHOLD_LOG_MIN =
                (float)Math.log(MATCH_BEST_TO_SECOND_BEST_THRESHOLD_MIN);
        private static final float MATCH_BEST_TO_SECOND_BEST_THRESHOLD_LOG_MAX =
                (float)Math.log(MATCH_BEST_TO_SECOND_BEST_THRESHOLD_MAX);

        public float rowThreshold;
        public float binMinimum;
        public float zoneThreshold;
        public int zoneCount;
        public float zoneCountThreshold;
        public float matchBaseThreshold;
        public float matchBestToSecondBestThreshold;

        public Parameters() {
        }

        public Parameters(Parameters other) {
            rowThreshold = other.rowThreshold;
            binMinimum = other.binMinimum;
            zoneThreshold = other.zoneThreshold;
            zoneCount = other.zoneCount;
            zoneCountThreshold = other.zoneCountThreshold;
            matchBaseThreshold = other.matchBaseThreshold;
            matchBestToSecondBestThreshold = other.matchBestToSecondBestThreshold;
        }

        public static Parameters makeRandom(Random r) {
            Parameters p = new Parameters();

            p.rowThreshold = randLogFloat(r, ROW_THRESHOLD_LOG_MIN, ROW_THRESHOLD_LOG_MAX);
            p.binMinimum = randFloat(r, BIN_MINIMUM_MIN, BIN_MINIMUM_MAX);
            p.zoneThreshold = randLogFloat(r, ZONE_THRESHOLD_LOG_MIN, ZONE_THRESHOLD_LOG_MAX);
            p.zoneCount = randInt(r, ZONE_COUNT_MIN, ZONE_COUNT_MAX);
            p.zoneCountThreshold = randFloat(r, ZONE_COUNT_THRESHOLD_MIN, ZONE_COUNT_THRESHOLD_MAX);
            p.matchBaseThreshold = randFloat(r, MATCH_BASE_THRESHOLD_MIN, MATCH_BASE_THRESHOLD_MAX);
            p.matchBestToSecondBestThreshold = randLogFloat(r, MATCH_BEST_TO_SECOND_BEST_THRESHOLD_LOG_MIN,
                    MATCH_BEST_TO_SECOND_BEST_THRESHOLD_LOG_MAX);

            return p;
        }

        private static int randInt(Random r, int min, int max) {
            return r.nextInt(max - min + 1) + min;
        }

        private static float randFloat(Random r, float min, float max) {
            return r.nextFloat() * (max - min) + min;
        }

        private static float randLogFloat(Random r, float logMin, float logMax) {
            return (float)Math.exp(r.nextFloat() * (logMax - logMin) + logMin);
        }

        public Parameters mutate(Random r, float distance) {
            Parameters p = new Parameters(this);

            switch(r.nextInt(7)) {
                case 0:
                    p.rowThreshold = (float)Math.exp((1f - distance) * (float)Math.log(p.rowThreshold) +
                            distance * randFloat(r, ROW_THRESHOLD_LOG_MIN, ROW_THRESHOLD_LOG_MAX));
                    break;
                case 1:
                    p.binMinimum = (1f - distance) * p.binMinimum +
                            distance * randFloat(r, BIN_MINIMUM_MIN, BIN_MINIMUM_MAX);
                    break;
                case 2:
                    p.zoneThreshold = (float)Math.exp((1f - distance) * (float)Math.log(p.zoneThreshold) +
                            distance * randFloat(r, ZONE_THRESHOLD_LOG_MIN, ZONE_THRESHOLD_LOG_MAX));
                    break;
                case 3:
                    p.zoneCount = (int)Math.rint((1f - distance) * p.zoneCount +
                            distance * randInt(r, ZONE_COUNT_MIN, ZONE_COUNT_MAX));
                    break;
                case 4:
                    p.zoneCountThreshold = (1f - distance) * p.zoneCountThreshold +
                            distance * randFloat(r, ZONE_COUNT_THRESHOLD_MIN, ZONE_COUNT_THRESHOLD_MAX);
                    break;
                case 5:
                    p.matchBaseThreshold = (1f - distance) * p.matchBaseThreshold +
                            distance * randFloat(r, MATCH_BASE_THRESHOLD_MIN, MATCH_BASE_THRESHOLD_MAX);
                    break;
                case 6:
                    p.matchBestToSecondBestThreshold =
                            (float)Math.exp((1f - distance) * (float)Math.log(p.matchBestToSecondBestThreshold) +
                                    distance * randFloat(r, MATCH_BEST_TO_SECOND_BEST_THRESHOLD_LOG_MIN,
                                            MATCH_BEST_TO_SECOND_BEST_THRESHOLD_LOG_MAX));
                    break;
            }
            return p;
        }

        public Parameters mutateAll(Random r, float distance) {
            Parameters p = new Parameters(this);

            p.rowThreshold = (float)Math.exp((1f - distance) * (float)Math.log(p.rowThreshold) +
                    distance * randFloat(r, ROW_THRESHOLD_LOG_MIN, ROW_THRESHOLD_LOG_MAX));
            p.binMinimum = (1f - distance) * p.binMinimum +
                    distance * randFloat(r, BIN_MINIMUM_MIN, BIN_MINIMUM_MAX);
            p.zoneThreshold = (float)Math.exp((1f - distance) * (float)Math.log(p.zoneThreshold) +
                    distance * randFloat(r, ZONE_THRESHOLD_LOG_MIN, ZONE_THRESHOLD_LOG_MAX));
            p.zoneCount = (int)Math.rint((1f - distance) * p.zoneCount +
                    distance * randInt(r, ZONE_COUNT_MIN, ZONE_COUNT_MAX));
            p.zoneCountThreshold = (1f - distance) * p.zoneCountThreshold +
                    distance * randFloat(r, ZONE_COUNT_THRESHOLD_MIN, ZONE_COUNT_THRESHOLD_MAX);
            p.matchBaseThreshold = (1f - distance) * p.matchBaseThreshold +
                    distance * randFloat(r, MATCH_BASE_THRESHOLD_MIN, MATCH_BASE_THRESHOLD_MAX);
            p.matchBestToSecondBestThreshold =
                    (float)Math.exp((1f - distance) * (float)Math.log(p.matchBestToSecondBestThreshold) +
                            distance * randFloat(r, MATCH_BEST_TO_SECOND_BEST_THRESHOLD_LOG_MIN,
                                    MATCH_BEST_TO_SECOND_BEST_THRESHOLD_LOG_MAX));

            return p;
        }

        public Parameters combine(Random r, Parameters other) {
            Parameters p = new Parameters();

            p.rowThreshold = r.nextBoolean() ? rowThreshold : other.rowThreshold;
            p.binMinimum = r.nextBoolean() ? binMinimum : other.binMinimum;
            p.zoneThreshold = r.nextBoolean() ? zoneThreshold : other.zoneThreshold;
            p.zoneCount = r.nextBoolean() ? zoneCount : other.zoneCount;
            p.zoneCountThreshold = r.nextBoolean() ? zoneCountThreshold : other.zoneCountThreshold;
            p.matchBaseThreshold = r.nextBoolean() ? matchBaseThreshold : other.matchBaseThreshold;
            p.matchBestToSecondBestThreshold = r.nextBoolean() ? matchBestToSecondBestThreshold :
                    other.matchBestToSecondBestThreshold;

            return p;
        }
    }

    public float rowThreshold = 0.326846f;
    public float binMinimum = -0.130795f;
    public float zoneThreshold = 0.171726f;
    public int zoneCount = 11;
    public int zoneCountThreshold = 2;

    public TimeCorrelatingRecognizer(CodeLibrary library, FrequencyTransformer frequencyTransformer) {
        super(library, frequencyTransformer, 0.244680f, 0.126661f);
    }

    public TimeCorrelatingRecognizer(CodeLibrary library, FrequencyTransformer frequencyTransformer, Parameters params) {
        super(library, frequencyTransformer, params.matchBaseThreshold, params.matchBestToSecondBestThreshold);

        rowThreshold = params.rowThreshold;
        binMinimum = params.binMinimum;
        zoneThreshold = params.zoneThreshold;
        zoneCount = params.zoneCount;
        zoneCountThreshold = (int) Math.rint(params.zoneCountThreshold * zoneCount);
    }

    public float matchQuality(CodeLibrary.Fingerprint fp, float[] inputBinRows) {
        int rows = fp.getMatchRows();
        float q = 1f;
        int zoneHits = 0;
        int zoneSamples = 0;
        int lastZone = 0;
        int hits = 0;
        for (int i = 0; i < rows; ++i) {
            float patternSum = 0f;
            for (int p: fp.getPattern()[i]) {
                patternSum += Math.max(inputBinRows[p], binMinimum);
            }
            if (patternSum > rowThreshold) {
                ++zoneHits;
            }
            ++zoneSamples;

            int zone = (i + 1) * zoneCount / rows;
            if (zone != lastZone && zoneSamples > 0) {
                float zoneQ = (float) zoneHits / zoneSamples;
                q *= zoneQ;
                if(zoneQ >= zoneThreshold) {
                    ++hits;
                }
                lastZone = zone;
                zoneHits = 0;
                zoneSamples = 0;
            }
        }

        return hits >= zoneCountThreshold ? q : 0f;
    }
}
