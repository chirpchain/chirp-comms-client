package com.cherrydev.chirpcommsclient.chirpmodem;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.cherrydev.chirpcommsclient.util.AudioConvert;

import ca.vectorharmony.chirpmodem.AudioTransmitter;

/**
 * Created by jlunder on 6/29/15.
 */
public class SpeakerAudioTransmitter extends AudioTransmitter {

    private AudioTrack audioTrack;
    private boolean rightChannel;
    private int sampleRate;
    private int bufferSamples;
    private int bufferFillSamples;

    public void initOnThisThread(boolean rightChannel, int sampleRate) {
        this.rightChannel = rightChannel;
        this.sampleRate = sampleRate;
        this.bufferSamples = sampleRate; // (1 second)
        // Stereo sample size is 64 bits
        int bufSize = 2 * bufferSamples; // 32 bits * 2 * 1 second
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        bufSize = Math.max(bufSize, minBufferSize);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufSize, AudioTrack.MODE_STREAM);

        int periodicNotificationSamples = sampleRate / 4;
        audioTrack.setPositionNotificationPeriod(sampleRate / 4);
        audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {

            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {
                bufferFillSamples = Math.max(bufferFillSamples - periodicNotificationSamples, 0);

            }
        });
        audioTrack.play();
    }

    @Override
    public int getAvailableBuffer() {
        return bufferSamples - bufferFillSamples;
    }

    @Override
    public void writeAudioBuffer(float[] buf) {
        short[] pcm = AudioConvert.convertToStereoPcm(buf, 0, buf.length, rightChannel);
        audioTrack.write(pcm, 0, pcm.length);
        bufferFillSamples += buf.length / 2;
    }

    @Override
    public void stop() {
        audioTrack.stop();
        audioTrack.release();
    }

}
