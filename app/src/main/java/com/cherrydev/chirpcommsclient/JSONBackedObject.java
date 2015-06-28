package com.cherrydev.chirpcommsclient;

import android.support.annotation.Nullable;

import org.json.JSONObject;


public interface JSONBackedObject {
    void setFromJson(JSONObject json);
    JSONObject getJson(@Nullable JSONObject existingObject);
}
