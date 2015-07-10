package com.cherrydev.chirpcommsclient.util;

import android.support.annotation.Nullable;
import android.util.Log;

import com.cherrydev.chirpcommsclient.socketmessages.JSONBackedObject;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by alannon on 2015-07-06.
 */
public class ChirpNode implements JSONBackedObject {
    private String TAG_NODE;
    private byte id;
    private String name;
    private boolean acoustic;
    private ChirpNode forwardAcousticNode;
    private ChirpNode backwardAcousticNode;
    private byte forwardAcousticNodeId = -1;
    private byte backwardAcousticNodeId = -1;

    public ChirpNode(byte id, String name, boolean acoustic, byte forwardAcousticNodeId, byte backwardAcousticNodeId) {
        this.id = id;
        this.name = name;
        this.acoustic = acoustic;
        this.forwardAcousticNodeId = forwardAcousticNodeId;
        this.backwardAcousticNodeId = backwardAcousticNodeId;
        TAG_NODE = "ChirpNode" + id;
    }

    public ChirpNode(JSONObject json) throws JSONException {
        setFromJson(json);
        TAG_NODE = "ChirpNode" + id;
    }

    public byte getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setAcoustic(boolean acoustic) {
        this.acoustic = acoustic;
    }

    public boolean isAcoustic() {
        return acoustic;
    }

    public byte getForwardAcousticNodeId() {
        return forwardAcousticNodeId;
    }

    public ChirpNode getForwardAcousticNode() {
        return forwardAcousticNode;
    }

    public byte getBackwardAcousticNodeId() {
        return backwardAcousticNodeId;
    }

    public ChirpNode getBackwardAcousticNode() {
        return backwardAcousticNode;
    }

    public void setForwardAcousticNode(ChirpNode forwardAcousticNode) {
        if (forwardAcousticNode != null) {
            if (forwardAcousticNode.getId() != forwardAcousticNodeId) {
                Log.w(TAG_NODE, "Tried to be assigned a non-matching forward acoustic node");
                this.forwardAcousticNode = null;
                return;
            }
        }
        this.forwardAcousticNode = forwardAcousticNode;
    }
    public void setForwardAcousticNodeId(byte forwardAcousticNodeId) {
        this.forwardAcousticNodeId = forwardAcousticNodeId;
    }
    public void setBackwardAcousticNode(ChirpNode backwardAcousticNode) {
        if (backwardAcousticNode != null) {
            if (backwardAcousticNode.getId() != backwardAcousticNodeId) {
                Log.w(TAG_NODE, "Tried to be assigned a non-matching backward acoustic node");
                this.backwardAcousticNode = null;
                return;
            }
        }
        this.backwardAcousticNode = backwardAcousticNode;
    }

    public void setBackwardAcousticNodeId(byte backwardAcousticNodeId) {
        this.backwardAcousticNodeId = backwardAcousticNodeId;
    }

    @Override
    public void setFromJson(JSONObject json) throws JSONException {
        id = (byte) json.getInt("id");
        name = json.getString("name");
        acoustic = json.getBoolean("acoustic");
        forwardAcousticNodeId = (byte) json.optInt("forwardAcousticNodeId");
        backwardAcousticNodeId = (byte) json.optInt("backwardAcousticNodeId");
    }

    @Override
    public JSONObject getJson(@Nullable JSONObject json) {
        if (json ==  null) {
            json = new JSONObject();
        }
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("acoustic", acoustic);
            if (forwardAcousticNodeId >= 0) json.put("forwardAcousticNodeId", forwardAcousticNodeId);
            if (backwardAcousticNodeId >= 0) json.put("backwardAcousticNodeId", backwardAcousticNodeId);
        }
        catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
