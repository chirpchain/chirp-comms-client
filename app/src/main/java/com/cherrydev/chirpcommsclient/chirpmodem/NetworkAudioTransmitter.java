package com.cherrydev.chirpcommsclient.chirpmodem;

import android.os.Handler;
import android.util.Log;

import com.cherrydev.chirpcommsclient.util.AudioConvert;
import com.cherrydev.usbaudiodriver.AudioPlayback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    private FloatBuffer floatBuf; // Assume in write mode
    private Handler handler;
    private Consumer<short[]> networkSender;
    private boolean monitorAudio;
    private Timer timer;

    public void initOnThisThread(int sampleRate, Consumer<short[]> networkSender, boolean monitorAudio) {
        if (monitorAudio) AudioPlayback.setup(sampleRate);
        this.networkSender = networkSender;
        this.monitorAudio = monitorAudio;
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
        int samplesToSend = floatBuf.capacity() / SENDS_PER_SECOND;
        int availableToRead = getAvailableBytes();
        if (availableToRead > 0) {
            samplesToSend = Math.min(samplesToSend, availableToRead);
            float[] floatSamples = new float[samplesToSend];
            floatBuf.flip();
            floatBuf.get(floatSamples);
            floatBuf.compact();
            //Log.d(TAG_CLASS, "Had " + availableToRead + " available, sent " + samplesToSend + " position is " + floatBuf.position());
            short[] pcm = AudioConvert.convertToPcm(floatSamples, 0, floatSamples.length);
            if (monitorAudio) {
                ByteBuffer bb = ByteBuffer.allocate(pcm.length * 2);
                bb.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);
                bb.position(0);
                AudioPlayback.write(bb.array(), bb.capacity());
            }
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
