package com.cherrydev.chirpcommsclient.chirpmodem;

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
        return floatFromPcm(pcmSamples);
    }

    public synchronized void receiveAudioData(byte[] sampleData, int sampleRate) {
        buf.compact();
        int dropped = 0;
        if (sampleData.length > buf.limit()) {
            dropped = sampleData.length - buf.limit();
        }
        buf.put(sampleData, 0, sampleData.length - dropped);
        this.droppedSamples = dropped / 2;
    }

    private float[] floatFromPcm(short[] samples) {
        float[] floats = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            short s = samples[i];
            float f = s * (1f / 32768f);
            floats[i] = f;
        }
        return floats;
    }
}
