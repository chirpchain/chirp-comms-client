package com.cherrydev.chirpcommsclient.messages;

import com.cherrydev.chirpcommsclient.socketmessages.ChirpSocketMessage;

import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumSet;

public class ChirpMessage {
    private byte mFrom;
    private byte mTo;
    private String sender;
    private String recipient;
    private EnumSet<MessageFlags> mFlags = EnumSet.noneOf(MessageFlags.class);
    private String message;

    public ChirpMessage(byte mFrom, byte mTo, String sender, String recipient, EnumSet<MessageFlags> mFlags, String message) {
        this.mFrom = mFrom;
        this.mTo = mTo;
        this.sender = sender;
        this.recipient = recipient;
        this.mFlags = mFlags;
        this.message = message;
    }

    public byte getFrom() {
        return mFrom;
    }

    public void setFrom(byte mFrom) {
        this.mFrom = mFrom;
    }

    public byte getTo() {
        return mTo;
    }

    public void setTo(byte mTo) {
        this.mTo = mTo;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public byte getFlagByte() {
        return getFlagValue();
    }

    public void setFlag(MessageFlags flag) {
        mFlags.add(flag);
    }

    public void removeFlag(MessageFlags flag) {
        mFlags.remove(flag);
    }

    public ChirpMessage(byte[] bytes) {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            byte type = dis.readByte();
            if (type != MessageType.ChirpMessage.typeValue) {
                throw new IllegalArgumentException("This isn't a chirp message!");
            }
            mFrom = dis.readByte();
            mTo = dis.readByte();
            sender = dis.readUTF();
            recipient = dis.readUTF();
            setFlagValue(dis.readByte());
            message = dis.readUTF();
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bytes);
        try {
            dos.writeByte(MessageType.ChirpMessage.typeValue);
            dos.writeByte(mFrom);
            dos.writeByte(mTo);
            dos.writeUTF(sender);
            dos.writeUTF(recipient);
            dos.writeByte(getFlagValue());
            dos.writeUTF(message);
            dos.flush();
            return bytes.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static EnumSet<MessageFlags> flagSetFromByte(byte flag) {
        EnumSet<MessageFlags> flagSet = EnumSet.noneOf(MessageFlags.class);
        for(MessageFlags val : MessageFlags.values()) {
            byte flagVal = val.getStatusFlagValue();
            if ( (flagVal&flag) == flagVal ) {
                flagSet.add(val);
            }
        }
        return flagSet;
    }

    public static byte flagByteFromSet(EnumSet<MessageFlags> flagSet) {
        byte value = 0;
        for(MessageFlags flag : flagSet) {
            value |= flag.getStatusFlagValue();
        }
        return value;
    }

    private void setFlagValue(byte flag) {
        mFlags = flagSetFromByte(flag);
    }

    private byte getFlagValue() {
        return flagByteFromSet(mFlags);
    }

    @Override
    public String toString() {
        try {
            return new ChirpSocketMessage((byte)0,(byte)0, this).getJson(null).toString(2);
        } catch (JSONException e) {
            return "[Exception while encoding in toString()!]";
        }
    }

    public enum MessageFlags {
        Public;

        public byte getStatusFlagValue(){
            return (byte) (1 << this.ordinal());
        }
    }
}
