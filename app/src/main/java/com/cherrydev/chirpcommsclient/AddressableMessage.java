package com.cherrydev.chirpcommsclient;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by alannon on 2015-06-25.
 */
public class AddressableMessage implements JSONBackedObject {
    private String from;
    private String to;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public AddressableMessage() {

    }

    public AddressableMessage(JSONObject json) {
        setFromJson(json);
    }

    public AddressableMessage(String from, String to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public void setFromJson(JSONObject json) {

        try {
            setFrom(json.getString("from"));
            setTo(json.getString("to"));
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
