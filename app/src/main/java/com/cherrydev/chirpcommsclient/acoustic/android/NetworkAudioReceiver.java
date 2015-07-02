package com.cherrydev.chirpcommsclient.acoustic.android;

import com.cherrydev.chirpcommsclient.acoustic.AudioReceiver;

/**
 * Created by jlunder on 6/29/15.
 */
public class NetworkAudioReceiver extends AudioReceiver{
    public int getAndResetDroppedSampleCount() {
        return 0;
    }

    public float[] readAudioBuffer() {
        return null;
    }
}
