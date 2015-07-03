package com.cherrydev.chirpcommsclient.util;

public class AudioConvert {
    public static short[] convertToStereoPcm(float[] floatSamples, boolean rightChannel) {
        short[] pcm = new short[floatSamples.length * 2];
        for( int i = 0; i < floatSamples.length; i++) {
            float f = floatSamples[i];
            short s = (short)Math.max(-32768, Math.min(32767, (int)(f * 32768f)));
            pcm[ (i * 2) + (rightChannel ? 1 : 0)] = s;
        }
        return pcm;
    }

    public static short[] convertToPcm(float[] floatSamples) {
        short[] pcm = new short[floatSamples.length];
        for( int i = 0; i < floatSamples.length; i++) {
            float f = floatSamples[i];
            pcm[i] = (short)Math.max(-32768, Math.min(32767, (int)(f * 32768f)));
        }
        return pcm;
    }

    public static float[] convertToFloat(short[] pcmSamples) {
        float[] floats = new float[pcmSamples.length];
        for (int i = 0; i < pcmSamples.length; i++) {
            short s = pcmSamples[i];
            float f = s * (1f / 32768f);
            floats[i] = f;
        }
        return floats;
    }
}
