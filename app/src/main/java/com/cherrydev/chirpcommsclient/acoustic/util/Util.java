package com.cherrydev.chirpcommsclient.acoustic.util;

/**
 * Created by jlunder on 6/29/15.
 */
public class Util {
    public static float max(float[] values) {
        float maxValue = values[0];
        for (int i = 0; i < values.length; ++i) {
            maxValue = Math.max(maxValue, values[i]);
        }
        return maxValue;
    }

    public static float mean(float[] vals) {
        float sum = 0f;

        for (int i = 0; i < vals.length; ++i) {
            sum += vals[i];
        }

        return sum / vals.length;
    }

    public static float[] join(float[] a, float[] b) {
        float[] c = new float[(a == null ? 0 : a.length) + (b == null ? 0 : b.length)];
        int offset = 0;

        if(a != null) {
            System.arraycopy(a, 0, c, 0, a.length);
            offset = a.length;
        }
        if(b != null) {
            System.arraycopy(b, 0, c, offset, b.length);
        }

        return c;
    }
}
