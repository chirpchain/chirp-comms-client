package com.cherrydev.chirpcommsclient.chirpmodem;

import android.os.Handler;
import android.util.Log;

import com.cherrydev.chirpcommsclient.util.AudioConvert;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Timer;
import java.util.TimerTask;

import ca.vectorharmony.chirpmodem.AudioTransmitter;
import java8.util.function.Consumer;

/**
 * Created by jlunder on 6/29/15.
 */
public class NetworkAudioTransmitter extends AudioTransmitter{

    private static final String TAG_CLASS = "NetworkAudioTransmitter";

    private static final int SENDS_PER_SECOND = 4;

    private FloatBuffer floatBuf;
    private Handler handler;
    private Consumer<short[]> networkSender;
    private Timer timer;

    public void initOnThisThread(int sampleRate, Consumer<short[]> networkSender) {
        this.networkSender = networkSender;
        ByteBuffer buf = ByteBuffer.allocateDirect(sampleRate * 4);
        floatBuf = buf.asFloatBuffer();
        handler = new Handler();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(NetworkAudioTransmitter.this::sendSamples);
            }
        }, 0, 1000 / SENDS_PER_SECOND);
    }

    @Override
    public void stop() {
        timer.cancel();
    }

    private void sendSamples() {
        int samplesToSend = 1000 / SENDS_PER_SECOND;
        if (getAvailableBuffer() > 0) {
            samplesToSend = Math.min(samplesToSend, getAvailableBuffer());
            Log.d(TAG_CLASS, "Sending " + samplesToSend + " samples");
            float[] floatSamples = new float[samplesToSend];
            floatBuf.get(floatSamples);
            short[] pcm = AudioConvert.convertToPcm(floatSamples, 0, floatSamples.length);
            networkSender.accept(pcm);
        }
    }

    @Override
    public synchronized int getAvailableBuffer() {
        floatBuf.flip();
        return floatBuf.remaining();
    }

    @Override
    public synchronized void writeAudioBuffer(float[] buf) {
        Log.d(TAG_CLASS, "Received " + buf.length + " audio from codec");
        floatBuf.compact();
        floatBuf.put(buf);
    }

}
