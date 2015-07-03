package ca.vectorharmony.chirpmodem;

import ca.vectorharmony.chirpmodem.util.CodeLibrary;
import ca.vectorharmony.chirpmodem.util.FrequencyTransformer;

/**
 * Created by jlunder on 6/29/15.
 */
public class Modulator {
    public static final int MAX_SYMBOL_BUFFER = PacketCodec.MAX_PACKET_SYMBOLS * 2;

    static final CodeLibrary library = CodeLibrary.makeChirpCodes();

    private static final float[] idleSilence = new float[
            (int)Math.ceil(0.5f * library.getMinCodeLength() * FrequencyTransformer.SAMPLE_RATE)];

    private AudioTransmitter transmitter;
    private int[] sendQueue = new int[MAX_SYMBOL_BUFFER];
    private int sendQueueHead = 0;
    private int sendQueueTail = 0;

    public CodeLibrary getLibrary() {
        return library;
    }

    public Modulator(AudioTransmitter transmitter) {
        this.transmitter = transmitter;
    }

    public void sendSymbols(int[] symbols) {
        if(getSendQueueFree() < symbols.length) {
            throw new IllegalStateException("Trying to send a message but the send queue is full!");
        }
        for(int sym : symbols) {
            sendQueue[sendQueueHead++] = sym;
            sendQueueHead %= sendQueue.length;
        }
    }

    public void delaySend(int delaySymbols) {
        if(getSendQueueFree() < delaySymbols) {
            throw new IllegalStateException("Trying to delay but the send queue is full!");
        }
        for(int i = 0; i < delaySymbols; ++i) {
            sendQueue[sendQueueHead++] = -1;
            sendQueueHead %= sendQueue.length;
        }
    }

    public int getSendQueueFree() {
        return sendQueue.length - 1 - getSendQueueUsed();
    }

    public int getSendQueueUsed() {
        return (sendQueueHead + sendQueue.length - sendQueueTail) % sendQueue.length;
    }

    public boolean isSendQueueEmpty() {
        return sendQueueTail != sendQueueHead;
    }

    public boolean isActive() {
        return !isSendQueueEmpty();
    }

    public void update() {
        while(!isSendQueueEmpty()) {
            int nextSym = sendQueue[sendQueueTail];
            float[] nextCode = getLibrary().getCodeForSymbol(nextSym);
            if(transmitter.getAvailableBuffer() < nextCode.length) {
                break;
            }
            transmitter.writeAudioBuffer(nextCode);
            sendQueueTail++;
            sendQueueTail %= sendQueue.length;
        }
        while(isSendQueueEmpty() && transmitter.getAvailableBuffer() < idleSilence.length) {
            transmitter.writeAudioBuffer(idleSilence);
        }
    }
}
