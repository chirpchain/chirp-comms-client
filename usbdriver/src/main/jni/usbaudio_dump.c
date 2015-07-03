/*
 *
 * Dumb userspace USB Audio receiver
 * Copyright 2012 Joel Stanley <joel@jms.id.au>
 *
 * Based on the following:
 *
 * libusb example program to measure Atmel SAM3U isochronous performance
 * Copyright (C) 2012 Harald Welte <laforge@gnumonks.org>
 *
 * Copied with the author's permission under LGPL-2.1 from
 * http://git.gnumonks.org/cgi-bin/gitweb.cgi?p=sam3u-tests.git;a=blob;f=usb-benchmark-project/host/benchmark.c;h=74959f7ee88f1597286cd435f312a8ff52c56b7e
 *
 * An Atmel SAM3U test firmware is also available in the above repository.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <signal.h>
#include <stdbool.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include <libusb.h>

#include <jni.h>

#include <android/log.h>
#define LOGD(...) \
    __android_log_print(ANDROID_LOG_DEBUG, "UsbAudioNative", __VA_ARGS__)

#define UNUSED __attribute__((unused))

/* The first PCM mono AudioStreaming endpoint. */
#define EP_ISO_IN	0x82
// #define IFACE_NUM   2

#define USB_TYPE_CLASS (0x01 << 5)
#define USB_RECIP_INTERFACE 0x01
#define USB_DIR_IN 0x80
#define USB_DIR_OUT 0
#define GET_CUR  0x81
#define GET_MIN  0x82
#define GET_MAX  0x83
#define GET_RES  0x84
#define SET_CUR  0x01
#define VOLUME_CONTROL  0x02


static int do_exit = 1;
static struct libusb_device_handle *devh = NULL;
static int iface_num = 0;

static unsigned long num_bytes = 0, num_xfer = 0;
static struct timeval tv_start;

static JavaVM* java_vm = NULL;

static jclass au_id_jms_usbaudio_AudioPlayback = NULL;
static jmethodID au_id_jms_usbaudio_AudioPlayback_write;

static uint8_t* packet_buf = NULL;

static void cb_xfr(struct libusb_transfer *xfr)
{
	unsigned int i;

    int len = 0;

    // Get an env handle
    JNIEnv * env;
    void * void_env;
    bool had_to_attach = false;
    jint status = (*java_vm)->GetEnv(java_vm, &void_env, JNI_VERSION_1_6);

    if (status == JNI_EDETACHED) {
        (*java_vm)->AttachCurrentThread(java_vm, &env, NULL);
        had_to_attach = true;
    } else {
        env = void_env;
    }

    // Create a jbyteArray.
    int start = 0;
    jbyteArray audioByteArray = NULL;

    for (i = 0; i < xfr->num_iso_packets; i++) {
        struct libusb_iso_packet_descriptor *pack = &xfr->iso_packet_desc[i];
        if (i == 0) {
        	// use the length of the first pack
        	audioByteArray = (*env)->NewByteArray(env, pack->length * xfr->num_iso_packets);
        }

        if (pack->status != LIBUSB_TRANSFER_COMPLETED) {
            LOGD("Error (status %d: %s) :", pack->status,
                    libusb_error_name(pack->status));
            /* This doesn't happen, so bail out if it does. */
            exit(EXIT_FAILURE);
        }

        const uint8_t *data = libusb_get_iso_packet_buffer_simple(xfr, i);
        (*env)->SetByteArrayRegion(env, audioByteArray, len, pack->actual_length, data);

        len += pack->actual_length;
    }

    // Call write()
    (*env)->CallStaticVoidMethod(env, au_id_jms_usbaudio_AudioPlayback,
            au_id_jms_usbaudio_AudioPlayback_write, audioByteArray, len);
    (*env)->DeleteLocalRef(env, audioByteArray);
    if ((*env)->ExceptionCheck(env)) {
        LOGD("Exception while trying to pass sound data to java");
        return;
    }

	num_bytes += len;
	num_xfer++;

    if (had_to_attach) {
        (*java_vm)->DetachCurrentThread(java_vm);
    }


	if (libusb_submit_transfer(xfr) < 0) {
		LOGD("error re-submitting URB\n");
		exit(1);
	}
}

#define NUM_TRANSFERS 10
#define NUM_PACKETS 10

static int benchmark_in(uint8_t ep, int packetSize)
{
	static struct libusb_transfer *xfr[NUM_TRANSFERS];
	
	size_t bufSize = sizeof(uint8_t) * packetSize * NUM_PACKETS;
	packet_buf = calloc(sizeof(uint8_t), packetSize * NUM_PACKETS);
	int num_iso_pack = NUM_PACKETS;
    int i;

	/* NOTE: To reach maximum possible performance the program must
	 * submit *multiple* transfers here, not just one.
	 *
	 * When only one transfer is submitted there is a gap in the bus
	 * schedule from when the transfer completes until a new transfer
	 * is submitted by the callback. This causes some jitter for
	 * isochronous transfers and loss of throughput for bulk transfers.
	 *
	 * This is avoided by queueing multiple transfers in advance, so
	 * that the host controller is always kept busy, and will schedule
	 * more transfers on the bus while the callback is running for
	 * transfers which have completed on the bus.
	 */
    for (i=0; i<NUM_TRANSFERS; i++) {
        xfr[i] = libusb_alloc_transfer(num_iso_pack);
        if (!xfr[i]) {
            LOGD("Could not allocate transfer");
            return -ENOMEM;
        }

        libusb_fill_iso_transfer(xfr[i], devh, ep, packet_buf,
                bufSize, num_iso_pack, cb_xfr, NULL, 1000);
        libusb_set_iso_packet_lengths(xfr[i], bufSize/num_iso_pack);

        int ret = libusb_submit_transfer(xfr[i]);
        if (ret == -1) LOGD("IOERROR: %d", errno);
        else if (ret != 0) LOGD("ERROR: %s", libusb_error_name(ret));
    }

	gettimeofday(&tv_start, NULL);
	LOGD("Scheduled %d transfers", NUM_TRANSFERS);
    return 1;
}

unsigned int measure(void)
{
	struct timeval tv_stop;
	unsigned int diff_msec;

	gettimeofday(&tv_stop, NULL);

	diff_msec = (tv_stop.tv_sec - tv_start.tv_sec)*1000;
	diff_msec += (tv_stop.tv_usec - tv_start.tv_usec)/1000;

	printf("%lu transfers (total %lu bytes) in %u miliseconds => %lu bytes/sec\n",
		num_xfer, num_bytes, diff_msec, (num_bytes*1000)/diff_msec);

    return num_bytes;
}

JNIEXPORT jint JNICALL
Java_au_id_jms_usbaudio_UsbAudio_measure(JNIEnv* env UNUSED, jobject foo UNUSED) {
    return measure();
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved UNUSED)
{
    LOGD("libusbaudio: loaded");
    java_vm = vm;

    return JNI_VERSION_1_6;
}


JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved UNUSED)
{
    JNIEnv * env;
    void * void_env;
    (*java_vm)->GetEnv(vm, &void_env, JNI_VERSION_1_6);
    env = void_env;

    (*env)->DeleteGlobalRef(env, au_id_jms_usbaudio_AudioPlayback);

    LOGD("libusbaudio: unloaded");
}

JNIEXPORT jboolean JNICALL
Java_au_id_jms_usbaudio_UsbAudio_setup(JNIEnv* env UNUSED, jobject foo UNUSED, jint venderId, jint productId, jint endPoint, jint packetSize, jint interfaceNum)
{

	// libusb_set_debug(NULL, 2); Hah, this crashes!  Hilarious.

	int rc;

	rc = libusb_init(NULL);
	if (rc < 0) {
		LOGD("Error initializing libusb: %s\n", libusb_error_name(rc));
        return false;
	}

	devh = libusb_open_device_with_vid_pid(NULL, venderId, productId);
	iface_num = interfaceNum;
	if (!devh) {
		LOGD("Error finding USB device\n");
        libusb_exit(NULL);
        return false;
	}

	rc = libusb_kernel_driver_active(devh, 0);
	if (rc == 1) {
		rc = libusb_detach_kernel_driver(devh, 0);
		if (rc < 0) {
			LOGD("Could not detach kernel driver for control interface: %s\n",
					libusb_error_name(rc));
			libusb_close(devh);
			libusb_exit(NULL);
			return false;
		}
		LOGD("Detached control interface from kernel drive.");
	}

	rc = libusb_kernel_driver_active(devh, iface_num);
	if (rc == 1) {
		rc = libusb_detach_kernel_driver(devh, iface_num);
		if (rc < 0) {
			LOGD("Could not detach kernel driver for audio interface: %s\n",
					libusb_error_name(rc));
			libusb_close(devh);
			libusb_exit(NULL);
			return false;
		}
		LOGD("Detached audio interface from kernel drive.");
	}

	rc = libusb_claim_interface(devh, iface_num);
	if (rc < 0) {
		LOGD("Error claiming audio interface: %s\n", libusb_error_name(rc));
        libusb_close(devh);
        libusb_exit(NULL);
        return false;
    }

	rc = libusb_claim_interface(devh, 0);
	if (rc < 0) {
		LOGD("Error claiming control interface: %s\n", libusb_error_name(rc));
		libusb_close(devh);
		libusb_exit(NULL);
		return false;
	}

	rc = libusb_set_interface_alt_setting(devh, iface_num, 1);
	if (rc < 0) {
		LOGD("Error setting alt setting: %s\n", libusb_error_name(rc));
        libusb_close(devh);
        libusb_exit(NULL);
        return false;
	}

    // Get write callback handle
    jclass clazz = (*env)->FindClass(env, "au/id/jms/usbaudio/AudioPlayback"); 
    if (!clazz) {
        LOGD("Could not find au.id.jms.usbaudio.AudioPlayback");
        libusb_close(devh);
        libusb_exit(NULL);
        return false;
    }
    au_id_jms_usbaudio_AudioPlayback = (*env)->NewGlobalRef(env, clazz);

    au_id_jms_usbaudio_AudioPlayback_write = (*env)->GetStaticMethodID(env,
            au_id_jms_usbaudio_AudioPlayback, "write", "([BI)V");
    if (!au_id_jms_usbaudio_AudioPlayback_write) {
        LOGD("Could not find au.id.jms.usbaudio.AudioPlayback");
        (*env)->DeleteGlobalRef(env, au_id_jms_usbaudio_AudioPlayback);
        libusb_close(devh);
        libusb_exit(NULL);
        return false;
    }


    // Good to go
    do_exit = 0;
    LOGD("Starting capture");
	if ((rc = benchmark_in(endPoint, packetSize)) < 0) {
        LOGD("Capture failed to start: %d", rc);
        return false;
    }
    return true;
}

uint16_t getVolume() {
	int rc;
	uint16_t c_tr_result = 0;
	errno = 0;
	rc = libusb_control_transfer(devh,
			USB_TYPE_CLASS | USB_RECIP_INTERFACE | USB_DIR_IN,
			GET_CUR,
			(VOLUME_CONTROL << 8) | 0,
			0 | (8 << 8),
				(unsigned char*) &c_tr_result, sizeof(c_tr_result),
				0
			);
	if (rc >= 0) {
		LOGD("Reading existing volume, result was %#04x\n", c_tr_result);
	}
	else {
		LOGD("Error reading volume\n");
	}
	if (rc == -1) LOGD("IOERROR: %d\n", errno);
	else if (rc < 0) LOGD("ERROR: %s\n", libusb_error_name(rc));
	return c_tr_result;
}

JNIEXPORT jchar JNICALL
Java_au_id_jms_usbaudio_UsbAudio_setVolume(JNIEnv* env UNUSED, jobject foo UNUSED, jchar volume) {
	int rc;
	errno = 0;
	rc = libusb_control_transfer(devh,
			USB_TYPE_CLASS | USB_RECIP_INTERFACE | USB_DIR_OUT,
			SET_CUR,
			(VOLUME_CONTROL << 8) | 0,
				0 | (8 << 8),
				(unsigned char*) &volume, sizeof(uint16_t),
				0
				);
	//LOGD("Writing new volume, result was %d and data was %d\n", rc, volume);
	if (rc == -1) LOGD("IOERROR: %d", errno);
	else if (rc < 0) LOGD("ERROR: %s", libusb_error_name(rc));
	jchar newVolume = 0;
	newVolume = getVolume();
	return newVolume;
}



JNIEXPORT jint JNICALL
Java_au_id_jms_usbaudio_UsbAudio_getVolume(JNIEnv* env UNUSED, jobject foo UNUSED) {
	jint result = 0;
	result = getVolume();
	return result;
}


JNIEXPORT void JNICALL
Java_au_id_jms_usbaudio_UsbAudio_stop(JNIEnv* env UNUSED, jobject foo UNUSED) {
    do_exit = 1;
    measure();
}

JNIEXPORT bool JNICALL
Java_au_id_jms_usbaudio_UsbAudio_close(JNIEnv* env UNUSED, jobject foo UNUSED) {
    if (do_exit == 0) {
        return false;
    }
	libusb_release_interface(devh, iface_num);
	if (devh)
		libusb_close(devh);
	libusb_exit(NULL);
    if (packet_buf != NULL) {
    	free(packet_buf);
    }
    return true;
}


JNIEXPORT bool JNICALL
Java_au_id_jms_usbaudio_UsbAudio_loop(JNIEnv* env UNUSED, jobject foo UNUSED) {
	while (!do_exit) {
		int rc = libusb_handle_events(NULL);
		if (rc != LIBUSB_SUCCESS)
			return false;
	}
	return true;
}

