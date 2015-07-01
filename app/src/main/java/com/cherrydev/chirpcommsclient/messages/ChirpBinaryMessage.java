package com.cherrydev.chirpcommsclient.messages;

import com.cherrydev.chirpcommsclient.socketmessages.ChirpSocketMessage;

import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumSet;

public class ChirpBinaryMessage  {
    private ChirpMessage message;
    public ChirpBinaryMessage(ChirpMessage message) {
        this.message = message;
    }


    public ChirpBinaryMessage(byte[] bytes) {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            byte type = dis.readByte();
            if (type != MessageType.ChirpMessage.typeValue) {
                throw new IllegalArgumentException("This isn't a chirp message!");
            }
            byte from = dis.readByte();
            byte to = dis.readByte();
            int messageId = dis.readInt();
            EnumSet<IChirpMessage.MessageFlags> flags = ChirpMessage.flagSetFromByte(dis.readByte());
            String sender = dis.readUTF();
            String recipient = dis.readUTF();
            String messageStr = dis.readUTF();
            this.message = new ChirpMessage(from, to, messageId, flags, sender, recipient, messageStr);
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ChirpMessage getMessage() {
        return message;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bytes);
        try {
            dos.writeByte(MessageType.ChirpMessage.typeValue);
            dos.writeByte(message.getFrom());
            dos.writeByte(message.getTo());
            dos.writeInt(message.getMessageId());
            dos.writeByte(message.getFlagByte());
            dos.writeUTF(message.getSender());
            dos.writeUTF(message.getRecipient());
            dos.writeUTF(message.getMessage());
            dos.flush();
            return bytes.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        try {
            return new ChirpSocketMessage(message.getFrom(), message.getTo(), message).getJson(null).toString(2);
        } catch (JSONException e) {
            return "[Exception while encoding in toString()!]";
        }
    }

}
