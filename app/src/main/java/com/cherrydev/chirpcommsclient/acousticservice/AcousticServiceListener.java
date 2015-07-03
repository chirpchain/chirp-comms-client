package com.cherrydev.chirpcommsclient.acousticservice;

/**
 * Created by alannon on 2015-07-03.
 */
public interface AcousticServiceListener {
    void receiveAcousticMessage(byte from, byte to, byte[] message);
}
