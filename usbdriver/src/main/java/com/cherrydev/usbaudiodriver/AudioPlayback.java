package com.cherrydev.usbaudiodriver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Environment;
import android.util.Log;

public class AudioPlayback {
    private static final String TAG = "AudioPlayback";

    //private static final int SAMPLE_RATE_HZ = 48000;

    private static AudioTrack track = null;

    public static void setup(int sampleRate) {
        Log.i(TAG, "Audio Playback");

        int bufSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        Log.d(TAG, "Buf size: " + bufSize);

        track = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
                AudioTrack.MODE_STREAM);
        track.play();
    }

    public static int writeSamplesRemaining = 0;
    public static FileOutputStream stream;

    public static void write(byte[] decodedAudio, int bufLength) {
        if (writeSamplesRemaining > 0) {
            try {
                if (stream == null) {
                    stream = new FileOutputStream(Environment.getExternalStorageDirectory().getPath() + "/samples.bin");
                    Log.d(TAG, "Started writing samples");
                }
                stream.write(decodedAudio, 0, bufLength);
                writeSamplesRemaining--;
                if (writeSamplesRemaining == 0) {
                    stream.close();
                    stream = null;
                    Log.d(TAG, "Finished writing samples");
                }
            }
            catch (IOException ex) {
                throw new RuntimeException("This shouldn't happen!");
            }
        }
        track.write(decodedAudio, 0, bufLength);
    }
}
