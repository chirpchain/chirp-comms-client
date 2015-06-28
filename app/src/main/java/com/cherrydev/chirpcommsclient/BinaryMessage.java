package com.cherrydev.chirpcommsclient;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by alannon on 2015-06-25.
 */
public class BinaryMessage extends AddressableMessage {
    private byte[] data;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public BinaryMessage() {

    }

    public BinaryMessage(JSONObject json) {
        super(json);
    }

    public BinaryMessage(String from, String to, byte[] data) {
        super(from, to);
        this.data = data;
    }

    @Override
    public void setFromJson(JSONObject json) {
        super.setFromJson(json);
        try {
            setData((byte[]) json.get("data"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public JSONObject getJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        super.getJson(json);
        try {
            json.put("data", getData());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }
}
