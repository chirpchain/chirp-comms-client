package com.cherrydev.chirpcommsclient.messages;

public interface IChirpMessage {
    int getMessageId();

    void setMessageId(int messageId);

    byte getFrom();

    void setFrom(byte mFrom);

    byte getTo();

    void setTo(byte mTo);

    String getSender();

    void setSender(String sender);

    String getRecipient();

    void setRecipient(String recipient);

    String getMessage();

    void setMessage(String message);

    byte getFlagByte();

    void setFlag(MessageFlags flag);

    void removeFlag(MessageFlags flag);

    enum MessageFlags {
        Public;

        public byte getStatusFlagValue(){
            return (byte) (1 << this.ordinal());
        }
    }
}
