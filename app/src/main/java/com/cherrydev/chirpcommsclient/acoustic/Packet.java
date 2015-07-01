package com.cherrydev.chirpcommsclient.acoustic;

/**
 * Created by jlunder on 6/29/15.
 */
public class Packet {
    private byte from;
    private byte to;
    private byte[] payload;

    public void setFrom(byte from) {
        this.from = from;
    }

    public byte getFrom() {
        return from;
    }

    public void setTo(byte to) {
        this.to = to;
    }

    public byte getTo() {
        return to;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public byte[] getPayload() {
        return payload;
    }

    public Packet() {
    }
}
