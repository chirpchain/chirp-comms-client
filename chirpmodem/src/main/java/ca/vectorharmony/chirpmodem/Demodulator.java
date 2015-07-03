package ca.vectorharmony.chirpmodem;

import ca.vectorharmony.chirpmodem.util.CodeLibrary;
import ca.vectorharmony.chirpmodem.util.CodeRecognizer;
import ca.vectorharmony.chirpmodem.util.LiveFrequencyTransformer;
import ca.vectorharmony.chirpmodem.util.TimeCorrelatingRecognizer;

/**
 * Created by jlunder on 6/29/15.
 */
public class Demodulator {
    AudioReceiver receiver;
    CodeRecognizer recognizer;

    public CodeLibrary getLibrary() {
        return Modulator.library;
    }

    public Demodulator(AudioReceiver receiver) {
        this.receiver = receiver;
        this.recognizer = new TimeCorrelatingRecognizer(getLibrary(),
                new LiveFrequencyTransformer(true, false));
    }

    public boolean isCarrierPresent() {
        return hasNextReceivedSymbol();
    }

    public boolean hasNextReceivedSymbol() {
        update();
        return recognizer.hasNextSymbol();
    }

    public int nextReceivedSymbol() {
        update();
        return recognizer.nextSymbol();
    }

    public int getLastReceivedSymbolTime() {
        return recognizer.getLastSymbolTime();
    }

    private void update() {
        while(true) {
            float[] buf = receiver.readAudioBuffer();
            if(buf == null || buf.length == 0) {
                break;
            }
            ((LiveFrequencyTransformer)recognizer.getFrequencyTransformer()).addSamples(buf);
        }
    }
}
