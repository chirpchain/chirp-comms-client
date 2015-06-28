package com.cherrydev.chirpcommsclient;

import android.support.annotation.Nullable;

import org.json.JSONObject;

/**
 * Created by alannon on 2015-06-25.
 */
public interface JSONBackedObject {
    void setFromJson(JSONObject json);
    JSONObject getJson(@Nullable JSONObject existingObject);
}
