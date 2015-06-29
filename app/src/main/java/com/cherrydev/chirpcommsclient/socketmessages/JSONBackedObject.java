package com.cherrydev.chirpcommsclient.socketmessages;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;


public interface JSONBackedObject {
    void setFromJson(JSONObject json) throws JSONException;
    JSONObject getJson(@Nullable JSONObject existingObject);
}
