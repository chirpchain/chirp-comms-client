package com.cherrydev.chirpcommsclient.acoustic;

import com.cherrydev.chirpcommsclient.acoustic.util.CodeRecognizer;
import com.cherrydev.chirpcommsclient.acoustic.util.LiveFrequencyTransformer;
import com.cherrydev.chirpcommsclient.acoustic.util.TimeCorrelatingRecognizer;

/**
 * Created by jlunder on 6/29/15.
 */
public class Demodulator {
    AudioReceiver receiver;
    CodeRecognizer recognizer;

    public Demodulator(AudioReceiver receiver) {
        this.receiver = receiver;
        this.recognizer = new TimeCorrelatingRecognizer(Modulator.library,
                new LiveFrequencyTransformer(true, false));
    }

    public boolean hasNextReceivedSymbol() {
        update();
        return recognizer.hasNextSymbol();
    }

    public int nextReceivedSymbol() {
        update();
        return recognizer.nextSymbol();
    }

    private void update() {
        while(true) {
            float[] buf = receiver.readAudioBuffer();
            if(buf == null) {
                break;
            }
            ((LiveFrequencyTransformer)recognizer.getFrequencyTransformer()).addSamples(buf);
        }
    }
}
