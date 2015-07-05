package com.cherrydev.chirpcommsclient.chirpmodem;

import android.os.Environment;
import android.util.Log;

import com.cherrydev.chirpcommsclient.util.AudioConvert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import ca.vectorharmony.chirpmodem.AudioReceiver;

public class NetworkAudioReceiver extends AudioReceiver {
    private static final String TAG_COMPONENT = "NetworkAudioReceiver";
    private ByteBuffer buf;
    private int droppedSamples;

    public NetworkAudioReceiver(int sampleRate) {
        this.sampleRate = (float) sampleRate;
        buf = ByteBuffer.allocateDirect(sampleRate * 2); // 1S
    }

    @Override
    public synchronized int getAndResetDroppedSampleCount() {
        int dropped = droppedSamples;
        droppedSamples = 0;
        return dropped;
    }

    @Override
    public float[] readAudioBuffer() {
        return getFloatsFromBuffer();
    }

    @Override
    public void stop() {
    }


    //private int getCount = 0;
    private synchronized float[] getFloatsFromBuffer() {
        int availableBytes = buf.position();
        if (availableBytes == 0) return new float[0];
        buf.flip();
        ShortBuffer sb = buf.asShortBuffer();
        short[] pcmSamples = new short[availableBytes / 2];
        sb.get(pcmSamples);
        sb.clear();
        return AudioConvert.convertToFloat(pcmSamples, 0, pcmSamples.length);
    }


    public synchronized void receiveAudioData(byte[] sampleData, int sampleRate) {
        int dropped = 0;
        if (sampleData.length > buf.remaining()) {
            dropped = sampleData.length - buf.remaining();
        }
        buf.put(sampleData, 0, sampleData.length - dropped);
        Log.d(TAG_COMPONENT, "Put " + (sampleData.length - dropped) + " of " + sampleData.length + " samples into buffer.  Buffer position is " + buf.position());
        this.droppedSamples = dropped / 2;
    }


}
