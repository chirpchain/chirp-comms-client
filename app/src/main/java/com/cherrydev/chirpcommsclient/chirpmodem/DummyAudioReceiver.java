package com.cherrydev.chirpcommsclient.chirpmodem;

import ca.vectorharmony.chirpmodem.AudioReceiver;

public class DummyAudioReceiver extends AudioReceiver {
    @Override
    public int getAndResetDroppedSampleCount() {
        return 0;
    }

    @Override
    public float[] readAudioBuffer() {
        return new float[0];
    }

    @Override
    public void stop() {

    }
}
