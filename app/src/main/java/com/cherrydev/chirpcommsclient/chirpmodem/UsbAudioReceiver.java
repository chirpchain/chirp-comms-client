package com.cherrydev.chirpcommsclient.chirpmodem;

import ca.vectorharmony.chirpmodem.AudioReceiver;

/**
 * Created by jlunder on 6/29/15.
 */
public class UsbAudioReceiver extends AudioReceiver{
    public int getAndResetDroppedSampleCount() {
        return 0;
    }

    public float[] readAudioBuffer() {
        return null;
    }
}