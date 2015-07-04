package com.cherrydev.chirpcommsclient.chirpmodem;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.cherrydev.chirpcommsclient.util.AudioConvert;

import ca.vectorharmony.chirpmodem.AudioReceiver;

/**
 * Created by jlunder on 6/29/15.
 */
public class MicAudioReceiver extends AudioReceiver {
    public static final int NATIVE_SAMPLE_RATE = 41000; // This is really the only value guaranteed to work
    private AudioRecord record;
    private int nativeSamplesPerRead;

    public MicAudioReceiver() {
        sampleRate = 22050;
    }

    public int getAndResetDroppedSampleCount() {
        // We can't keep track of when AudioRecord drops samples and it's not worth it to
        // complicate the implementation just for that.
        return 0;
    }

    public static int getNativeSampleRate() {
        return NATIVE_SAMPLE_RATE;
    }

    public void initOnThisThread(int nativeSamplesPerRead) {
        this.nativeSamplesPerRead = nativeSamplesPerRead;
        record = new AudioRecord(MediaRecorder.AudioSource.MIC, NATIVE_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, NATIVE_SAMPLE_RATE * 2);
        record.startRecording();
    }

    public float[] readAudioBuffer() {
        short[] buf = new short[nativeSamplesPerRead];
        int numRead = record.read(buf, 0, buf.length);
        // throw away half the samples in-place
        for(int i = 0; i < numRead; i += 2) {
            buf[i / 2] = buf[i];
        }
        return AudioConvert.convertToFloat(buf, 0, numRead / 2);
    }
}
