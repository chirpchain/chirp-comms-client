package com.cherrydev.chirpcommsclient;

import org.json.JSONException;
import org.json.JSONObject;


public class AddressableMessage implements JSONBackedObject {
    private int from;
    private int to;

    public int getFrom() {
        return from;
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public int getTo() {
        return to;
    }

    public void setTo(int to) {
        this.to = to;
    }

    public AddressableMessage() {

    }

    public AddressableMessage(JSONObject json) {
        setFromJson(json);
    }

    public AddressableMessage(int from, int to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public void setFromJson(JSONObject json) {

        try {
            setFrom(json.getInt("from"));
            setTo(json.getInt("to"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public JSONObject getJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        try {
            json.put("to", getTo());
            json.put("from", getFrom());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }
}
