package ca.vectorharmony.chirpmodem;

/**
 * Created by jlunder on 6/29/15.
 */
public abstract class AudioTransmitter {
    public abstract boolean isActive();
    // to simplify other code, the buffer depth should be generously larger than (a) the amount of
    // time between calls to the Modulator's update() function and (b) the size of a symbol.
    // 0.5s-1s is probably the right ballpark.
    public abstract int getAvailableBuffer();
    public abstract void writeAudioBuffer(float[] buf);
}
