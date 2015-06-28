package com.cherrydev.chirpcommsclient;

import java.util.Set;

/**
 * Created by alannon on 2015-06-27.
 */
public class BaseCommsServiceListener implements ChirpCommsServiceListener {
    @Override
    public void peerIdSet(int peerId) {

    }

    @Override
    public void peerConnected(int newPeerId) {

    }

    @Override
    public void peerDisconnected(int peerId) {

    }

    @Override
    public void connected() {

    }

    @Override
    public void ready() {

    }

    @Override
    public void disconnected() {

    }

    @Override
    public void receiveAudioData(AudioDataMessage message) {

    }

    @Override
    public void receivePeerList(Set<Integer> peerIds) {

    }
}
