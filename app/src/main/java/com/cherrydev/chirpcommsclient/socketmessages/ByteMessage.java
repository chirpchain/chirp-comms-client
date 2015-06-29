package com.cherrydev.chirpcommsclient.socketmessages;


import com.cherrydev.chirpcommsclient.messages.MessageType;

import org.json.JSONException;
import org.json.JSONObject;

public class ByteMessage extends AddressableMessage {
    private byte[] mBytes;

    public ByteMessage(byte from, byte to, byte[] bytes) {
        super(from, to);
        mBytes = bytes;
    }

    public ByteMessage(JSONObject json) throws JSONException {
        super(json);
    }

    public MessageType getType() {
        if (mBytes.length == 0) return null;
        return MessageType.ofTypeValue(mBytes[0]);
    }

    public byte[] getBytes() {
        return mBytes;
    }

    public void setBytes(byte[] bytes) {
        mBytes = bytes;
    }

    @Override
    public JSONObject getJson(JSONObject json) {
        if (json == null) json = new JSONObject();
        super.getJson(json);
        try {
            json.put("bytes", mBytes);
        }
        catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

    @Override
    public void setFromJson(JSONObject json) throws JSONException {
        super.setFromJson(json);
        mBytes =  (byte[]) json.get("bytes");
    }
}
