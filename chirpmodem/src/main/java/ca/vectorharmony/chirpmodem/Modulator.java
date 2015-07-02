package ca.vectorharmony.chirpmodem;

import ca.vectorharmony.chirpmodem.util.CodeLibrary;

/**
 * Created by jlunder on 6/29/15.
 */
public class Modulator {
    int MAX_SYMBOL_BUFFER = PacketCodec.MAX_PACKET_SYMBOLS * 2;
    static CodeLibrary library = CodeLibrary.makeChirpCodes();

    private AudioTransmitter transmitter;
    private int[] sendQueue = new int[MAX_SYMBOL_BUFFER];
    private int sendQueueHead = 0;
    private int sendQueueTail = 0;

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
        return transmitter.isActive() || !isSendQueueEmpty();
    }

    public void update() {
        while(!isSendQueueEmpty()) {
            int nextSym = sendQueue[sendQueueTail];
            float[] nextCode = library.getCodeForSymbol(nextSym);
            if(transmitter.getAvailableBuffer() < nextCode.length) {
                break;
            }
            transmitter.writeAudioBuffer(nextCode);
            sendQueueTail++;
            sendQueueTail %= sendQueue.length;
        }
    }
}
