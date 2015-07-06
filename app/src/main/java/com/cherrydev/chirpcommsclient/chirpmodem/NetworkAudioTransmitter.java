package com.cherrydev.chirpcommsclient.chirpmodem;

import android.os.Handler;
import android.util.Log;

import com.cherrydev.chirpcommsclient.util.AudioConvert;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import ca.vectorharmony.chirpmodem.AudioTransmitter;
import java8.util.function.Consumer;

/**
 * Created by jlunder on 6/29/15.
 */
public class NetworkAudioTransmitter extends AudioTransmitter{

    private static final String TAG_CLASS = "NetworkAudioTransmitter";

    private static final int SENDS_PER_SECOND = 3;

    private FloatBuffer floatBuf; // Assume in write mode
    private Handler handler;
    private Consumer<short[]> networkSender;
    private Timer timer;
    private Random r = new Random();

    public void initOnThisThread(int sampleRate, Consumer<short[]> networkSender) {
        this.networkSender = networkSender;
        ByteBuffer buf = ByteBuffer.allocateDirect(sampleRate * 4);
        floatBuf = buf.asFloatBuffer();
        floatBuf.clear();
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
        int samplesToSend = floatBuf.capacity() / (SENDS_PER_SECOND - 1);
        int availableToRead = getAvailableBytes(); // A bit faster than it should be filling
        if (availableToRead > 0) {
            samplesToSend = Math.min(samplesToSend, availableToRead);
            float[] floatSamples = new float[samplesToSend];
            floatBuf.flip();
            floatBuf.get(floatSamples);
            floatBuf.compact();
            // Add a little noise to simulate real-world reception conditions
            for(int i = 0; i < floatSamples.length; ++i) {
                floatSamples[i] += r.nextFloat() * 1e-3f;
            }
            //Log.d(TAG_CLASS, "Had " + availableToRead + " available, sent " + samplesToSend + " position is " + floatBuf.position());
            short[] pcm = AudioConvert.convertToPcm(floatSamples, 0, floatSamples.length);
            networkSender.accept(pcm);
        }
    }

    private int getAvailableBytes() {
        return floatBuf.position();
    }

    @Override
    public synchronized int getAvailableBuffer() {
        return floatBuf.remaining();
    }

    @Override
    public synchronized void writeAudioBuffer(float[] buf) {
        floatBuf.put(buf);
    }

}
