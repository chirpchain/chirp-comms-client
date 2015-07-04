package com.cherrydev.chirpcommsclient.chirpmodem;

import ca.vectorharmony.chirpmodem.AudioReceiver;

/**
 * Created by jlunder on 6/29/15.
 */
public class UsbAudioReceiver extends AudioReceiver {
    @Override
    public int getAndResetDroppedSampleCount() {
        return 0;
    }

    @Override
    public float[] readAudioBuffer() {
        return null;
    }

    @Override
    public void stop() {

    }
}
