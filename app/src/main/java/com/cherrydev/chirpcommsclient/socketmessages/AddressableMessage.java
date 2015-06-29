package com.cherrydev.chirpcommsclient.socketmessages;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Note that the message to and from here represent the IMMEDIATE recipient of the message
 * and not the ultimate destination, as represented in the ChirpMessage, which may get relayed
 * before reaching the destination specified in that class.
 */
public class AddressableMessage implements JSONBackedObject {
    private byte from;
    private byte to;

    public byte getFrom() {
        return from;
    }

    public void setFrom(byte from) {
        this.from = from;
    }

    public byte getTo() {
        return to;
    }

    public void setTo(byte to) {
        this.to = to;
    }

    public AddressableMessage() {

    }

    public AddressableMessage(JSONObject json) throws JSONException {
        setFromJson(json);
    }

    public AddressableMessage(byte from, byte to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public void setFromJson(JSONObject json) throws JSONException {
        setFrom((byte) json.getInt("from"));
        setTo((byte) json.getInt("to"));
    }

    @Override
    public JSONObject getJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        try {
            json.put("to", getTo());
            json.put("from", getFrom());
        }
        catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }
}
