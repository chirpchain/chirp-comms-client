package com.cherrydev.chirpcommsclient.util;

public class AudioConvert {
    public static short[] convertToStereoPcm(float[] floatSamples, int offset, int length, boolean rightChannel) {
        short[] pcm = new short[length * 2];
        for( int i = 0; i < length; i++) {
            float f = floatSamples[i + offset];
            short s = (short)Math.max(-32768, Math.min(32767, (int)(f * 32768f)));
            pcm[ (i * 2) + (rightChannel ? 1 : 0)] = s;
        }
        return pcm;
    }

    public static short[] convertToPcm(float[] floatSamples, int offset, int length) {
        short[] pcm = new short[length];
        for( int i = 0; i < length; i++) {
            float f = floatSamples[i + offset];
            pcm[i] = (short)Math.max(-32768, Math.min(32767, (int)(f * 32768f)));
        }
        return pcm;
    }

    public static float[] convertToFloat(short[] pcmSamples, int offset, int length) {
        float[] floats = new float[length];
        for (int i = 0; i < length; i++) {
            short s = pcmSamples[i + offset];
            float f = s * (1f / 32768f);
            floats[i] = f;
        }
        return floats;
    }
}
