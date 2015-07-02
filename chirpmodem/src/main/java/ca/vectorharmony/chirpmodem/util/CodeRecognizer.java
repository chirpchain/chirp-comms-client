package ca.vectorharmony.chirpmodem.util;

/**
 * Created by jlunder on 6/10/15.
 */
public abstract class CodeRecognizer {
    protected CodeLibrary library;
    protected FrequencyTransformer frequencyTransformer;

    private boolean hasNextSymbol;
    private int nextSymbol;
    private float lastSymbolTime = 0f;
    private int rowsSinceSymbolDetected = 0;

    private float matchBaseThreshold;
    private float matchBestToSecondBestThreshold;

    public CodeLibrary getLibrary() {
        return library;
    }

    public FrequencyTransformer getFrequencyTransformer() {
        return frequencyTransformer;
    }

    public CodeRecognizer(CodeLibrary library, FrequencyTransformer frequencyTransformer, float matchBaseThreshold, float matchBestToSecondBestThreshold) {
        this.library = library;
        this.frequencyTransformer = frequencyTransformer;
        this.matchBaseThreshold = matchBaseThreshold;
        this.matchBestToSecondBestThreshold = matchBestToSecondBestThreshold;
    }

    public boolean matchQualityGraph(float[] results) {
        int resultRows = results.length / CodeLibrary.NUM_SYMBOLS;
        float bins[] = new float[library.getMaxCodeRows() * FrequencyTransformer.BINS_PER_ROW];
        for (int i = 0; i < resultRows; ++i) {
            frequencyTransformer.getRows(bins, library.getMaxCodeRows());
            for (int j = 0; j < CodeLibrary.NUM_SYMBOLS; ++j) {
                results[i * CodeLibrary.NUM_SYMBOLS + j] = matchQuality(library.getFingerprintForSymbol(j), bins);
            }
            frequencyTransformer.consumeRows(1);
        }
        return true;
    }

    public abstract float matchQuality(CodeLibrary.Fingerprint fp, float[] inputBinRows);

    public boolean hasNextSymbol() {
        if (!hasNextSymbol) {
            tryFillNextSymbol();
        }
        return hasNextSymbol;
    }

    public int nextSymbol() {
        int sym = nextSymbol;
        nextSymbol = -1;
        hasNextSymbol = false;
        return sym;
    }

    public float getLastSymbolTime() {
        return lastSymbolTime;
    }

    private void tryFillNextSymbol() {
        while (!hasNextSymbol && canMatch()) {
            int matchSym = tryFindMatch();

            if (matchSym != -1) {
                assert matchSym != -1;
                int codeRows = library.getFingerprintForSymbol(matchSym).getMatchRows();
                hasNextSymbol = true;
                rowsSinceSymbolDetected = 0;
                nextSymbol = matchSym;
                lastSymbolTime = frequencyTransformer.getTime();
                frequencyTransformer.consumeRows(codeRows - 2);
            } else {
                ++rowsSinceSymbolDetected;
                if (rowsSinceSymbolDetected > library.getMaxCodeRows()) {
                    hasNextSymbol = true;
                    rowsSinceSymbolDetected = 0;
                    nextSymbol = -1; // break
                }
                frequencyTransformer.consumeRows(1);
            }
        }
    }

    protected boolean canMatch() {
        return frequencyTransformer.getAvailableRows() >= library.getMaxCodeRows();
    }

    protected int tryFindMatch() {
        float[] inputBinRows = new float[library.getMaxCodeRows() * FrequencyTransformer.BINS_PER_ROW];
        int bestSym = -1;
        float bestQ = 0f;
        float secondQ = 0f;
        frequencyTransformer.getRows(inputBinRows, library.getMaxCodeRows());

        for (int i = 0; i < CodeLibrary.NUM_SYMBOLS; ++i) {
            CodeLibrary.Fingerprint fp = library.getFingerprintForSymbol(i);
            float q = matchQuality(fp, inputBinRows);
            if (q > bestQ) {
                secondQ = bestQ;
                bestQ = q;
                bestSym = i;
            }
        }

        if ((bestQ > matchBaseThreshold) && ((secondQ / bestQ) < matchBestToSecondBestThreshold)) {
            //System.out.print("Y!");
            return bestSym;
        } else {
            //System.out.println("n.");
            return -1;
        }
    }
}
