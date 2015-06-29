package com.cherrydev.chirpcommsclient.messages;

/**
 * Created by alannon on 2015-06-29.
 */
public enum MessageType {
    ChirpMessage((byte) 11);

    public final byte typeValue;

    MessageType(byte typeValue) {
        this.typeValue = typeValue;
    }

    public static MessageType ofTypeValue(byte typeValue) {
        for(MessageType t : MessageType.values()) {
            if (t.typeValue == typeValue) return t;
        }
        return null;
    }
}
