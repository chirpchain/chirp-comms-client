package com.cherrydev.chirpcommsclient.messages;

import java.util.EnumSet;

public class ChirpMessage implements IChirpMessage {

    private byte from;
    private byte to;
    private int messageId;
    private EnumSet<MessageFlags> flags = EnumSet.noneOf(MessageFlags.class);
    private String sender;
    private String recipient;
    private String message;

    public ChirpMessage(byte from, byte to, int messageId, EnumSet<MessageFlags> flags, String sender, String recipient, String message) {
        this.from = from;
        this.to = to;
        this.messageId = messageId;
        this.flags = flags;
        this.sender = sender;
        this.recipient = recipient;
        this.message = message;
    }

    @Override
    public int getMessageId() {
        return messageId;
    }

    @Override
    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    @Override
    public byte getFrom() {
        return from;
    }

    @Override
    public void setFrom(byte from) {
        this.from = from;
    }

    @Override
    public byte getTo() {
        return to;
    }

    @Override
    public void setTo(byte to) {
        this.to = to;
    }

    @Override
    public String getSender() {
        return sender;
    }

    @Override
    public void setSender(String sender) {
        this.sender = sender;
    }

    @Override
    public String getRecipient() {
        return recipient;
    }

    @Override
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public byte getFlagByte() {
        return getFlagValue();
    }

    @Override
    public void setFlag(MessageFlags flag) {
        flags.add(flag);
    }

    @Override
    public void removeFlag(MessageFlags flag) {
        flags.remove(flag);
    }

    private byte getFlagValue() {
        return flagByteFromSet(flags);
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
}
