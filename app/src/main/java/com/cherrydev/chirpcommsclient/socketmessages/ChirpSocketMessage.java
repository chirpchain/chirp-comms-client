package com.cherrydev.chirpcommsclient.socketmessages;

import com.cherrydev.chirpcommsclient.messages.ChirpBinaryMessage;
import com.cherrydev.chirpcommsclient.messages.ChirpMessage;

import org.json.JSONException;
import org.json.JSONObject;


public class ChirpSocketMessage extends AddressableMessage {

    private ChirpMessage message;

    public ChirpSocketMessage(byte from, byte to, ChirpMessage message) {
        super(from, to);
        this.message = message;
    }

    public ChirpSocketMessage(JSONObject json) throws JSONException {
        super(json);
    }

    public ChirpMessage getMessage() {
        return message;
    }

    public void setMessage(ChirpMessage message) {
        this.message = message;
    }

    @Override
    public void setFromJson(JSONObject json) throws JSONException {
        super.setFromJson(json);
        byte from = (byte) json.getInt("chirpFrom");
        byte to = (byte) json.getInt("chirpTo");
        int messageId = json.getInt("messageId");
        byte flag = (byte) json.getInt("flag");
        String sender = json.getString("sender");
        String recipient = json.getString("recipient");
        String message = json.getString("message");
        this.message = new ChirpMessage(from, to, messageId, ChirpMessage.flagSetFromByte(flag), sender, recipient, message);
    }

    @Override
    public JSONObject getJson(JSONObject json)  {
        if (json == null) json = new JSONObject();
        super.getJson(json);
        try {
            json.put("chirpFrom", this.message.getFrom());
            json.put("chirpTo", this.message.getTo());
            json.put("messageId", this.message.getMessageId());
            json.put("flag", this.message.getFlagByte());
            json.put("sender", this.message.getSender());
            json.put("recipient", this.message.getRecipient());
            json.put("message", this.message.getMessage());
        }
        catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }
}
