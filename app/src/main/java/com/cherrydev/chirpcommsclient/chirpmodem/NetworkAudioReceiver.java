package com.cherrydev.chirpcommsclient.chirpmodem;

import com.cherrydev.chirpcommsclient.util.AudioConvert;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import ca.vectorharmony.chirpmodem.AudioReceiver;

public class NetworkAudioReceiver extends AudioReceiver{

    private ByteBuffer buf;
    private int droppedSamples;

    public NetworkAudioReceiver(int sampleRate) {
        buf = ByteBuffer.allocateDirect(sampleRate * 2); // 1S
    }

    public synchronized int getAndResetDroppedSampleCount() {
        int dropped = droppedSamples;
        droppedSamples = 0;
        return dropped;
    }

    public float[] readAudioBuffer() {
        return getFloatsFromBuffer();
    }

    private synchronized float[] getFloatsFromBuffer() {
        buf.flip();
        ShortBuffer sb = buf.asShortBuffer();
        short[] pcmSamples = new short[sb.limit()];
        sb.get(pcmSamples);
        return AudioConvert.convertToFloat(pcmSamples);
    }

    public synchronized void receiveAudioData(byte[] sampleData, int sampleRate) {
        buf.compact();
        int dropped = 0;
        if (sampleData.length > buf.remaining()) {
            dropped = sampleData.length - buf.remaining();
        }
        buf.put(sampleData, 0, sampleData.length - dropped);
        this.droppedSamples = dropped / 2;
    }


}
