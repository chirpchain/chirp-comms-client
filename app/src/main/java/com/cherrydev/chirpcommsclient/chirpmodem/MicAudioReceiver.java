package com.cherrydev.chirpcommsclient.chirpmodem;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.cherrydev.chirpcommsclient.util.AudioConvert;

import ca.vectorharmony.chirpmodem.AudioReceiver;

/**
 * Created by jlunder on 6/29/15.
 */
public class MicAudioReceiver extends AudioReceiver {
    private static final String TAG = "MicAUdioReceiver";
    public static final int NATIVE_SAMPLE_RATE = 44100; // This is really the only value guaranteed to work
    private AudioRecord record;
    private int nativeSamplesPerRead;

    public MicAudioReceiver() {
        sampleRate = 22050;
    }

    @Override
    public int getAndResetDroppedSampleCount() {
        // We can't keep track of when AudioRecord drops samples and it's not worth it to
        // complicate the implementation just for that.
        return 0;
    }

    public static int getNativeSampleRate() {
        return NATIVE_SAMPLE_RATE;
    }

    public void initOnThisThread(int nativeSamplesPerRead) {
        int tmpBufferSize = AudioRecord.getMinBufferSize(NATIVE_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (tmpBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw new RuntimeException("Bad buffer size!!");
        }
        Log.d(TAG, "Initializing mic with sample rate " + NATIVE_SAMPLE_RATE + " and buffer size " + tmpBufferSize);
        this.nativeSamplesPerRead = nativeSamplesPerRead;
        record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, NATIVE_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, tmpBufferSize * 4);
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "Couldn't initialize AudioRecord!");
            record = null;
        }
        else{
            Log.i(TAG, "AudioRecord initialized!");
            record.startRecording();
        }
    }

    @Override
    public float[] readAudioBuffer() {
        if (record == null) return null;
        short[] buf = new short[nativeSamplesPerRead];
        int numRead = record.read(buf, 0, buf.length);
        if (numRead == AudioRecord.ERROR_INVALID_OPERATION || numRead == AudioRecord.ERROR_BAD_VALUE) {
            Log.w(TAG, "Error while reading!");
            return null;
        }
        else {
            Log.d(TAG, "Actually read " + numRead + " samples");
        }
        // throw away half the samples in-place
        for(int i = 0; i < numRead; i += 2) {
            buf[i / 2] = buf[i];
        }
        return AudioConvert.convertToFloat(buf, 0, numRead / 2);
    }

    @Override
    public void stop() {
        if (record != null) {
            record.stop();
            record.release();
        }
    }
}
