package com.cherrydev.chirpcommsclient.acoustic.android;

import com.cherrydev.chirpcommsclient.acoustic.AudioTransmitter;

/**
 * Created by jlunder on 6/29/15.
 */
public class NetworkAudioTransmitter extends AudioTransmitter{
    public boolean isActive() {
        return false;
    }

    public int getAvailableBuffer() {
        return 0;
    }

    public void writeAudioBuffer(float[] buf) {
    }
}
