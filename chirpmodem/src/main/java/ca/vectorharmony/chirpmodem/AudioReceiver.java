package ca.vectorharmony.chirpmodem;

/**
 * Created by jlunder on 6/29/15.
 */
public abstract class AudioReceiver {
    protected float sampleRate;

    public float getSampleRate() {
        return sampleRate;
    }

    public abstract int getAndResetDroppedSampleCount();
    public abstract float[] readAudioBuffer();
}
