package com.cherrydev.chirpcommsclient;

import org.json.JSONException;
import org.json.JSONObject;

public class AudioDataMessage extends AddressableMessage {
    private byte[] data;
    private int sampleRate;

    @SuppressWarnings("unused")
    public AudioDataMessage() {

    }

    public AudioDataMessage(JSONObject json) {
        super(json);
    }

    public AudioDataMessage(int from, int to, byte[] data, int sampleRate) {
        super(from, to);
        this.data = data;
        this.sampleRate = sampleRate;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    @Override
    public void setFromJson(JSONObject json) {
        super.setFromJson(json);
        try {
            setData((byte[]) json.get("data"));
            setSampleRate((int) json.get("sampleRate"));
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
            json.put("sampleRate", getSampleRate());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

}
