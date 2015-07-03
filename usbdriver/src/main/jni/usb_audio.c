// Super basic audio driver for libusb on android
// It can:
// 1) Initialize libusb.
// 2) Open USB Devies
// 3) Kernel-detach and claim the control interface (0x00) and audio interface (variable address) for device
// 4) Send input volume control messages
// 5) Initiate audio streaming
// Heavily based upon https://github.com/shenki/usbaudio-android-demo
// Modifications Copyright 2015 Avram Cherry <cherrydev@gmail.com>
// Original attribution and license follows:

/*
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
#include <usbstuff.h>

#define UNUSED __attribute__((unused))
#define NUM_TRANSFERS 10
#define NUM_PACKETS 10

static JavaVM* java_vm = NULL;

static jclass java_callback_class = NULL;
static jmethodID java_callback_method;
static bool is_init = false;
static bool stop_loop = false;

typedef struct usb_audio_device {
	// A unique value passed to setup() to identify the device to the callbacks
	jint tag;
	libusb_device_handle* dev_handle;
	size_t buf_len;
	int iface_num;
	uint8_t ep_addr;
	uint8_t control_unit_id;
	uint8_t* packet_buf;

} usb_audio_device_t;

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

    (*env)->DeleteGlobalRef(env, java_callback_class);

    LOGD("libusbaudio: unloaded");
}

jint throwRuntimeException( JNIEnv *env, char *message )
{
    jclass exClass;
    char *className = "java/lang/RuntimeException";

    exClass = (*env)->FindClass( env, className );
    return (*env)->ThrowNew( env, exClass, message );
}


usb_audio_device_t* alloc_audio_device(jint packetSize, jint interfaceNum, libusb_device_handle* devh, uint8_t ep_addr, jint tag, uint8_t controlUnitId) {
	usb_audio_device_t* audio_device = calloc(1, sizeof(usb_audio_device_t));
	audio_device->buf_len = sizeof(uint8_t) * packetSize * NUM_PACKETS;
	audio_device->packet_buf = NULL;
	audio_device->iface_num = interfaceNum;
	audio_device->dev_handle = devh;
	audio_device->ep_addr = ep_addr;
	audio_device->tag = tag;
	audio_device->control_unit_id = controlUnitId;
	return audio_device;
}

void free_audio_device(usb_audio_device_t* dev) {
	if (dev->packet_buf != NULL) free(dev->packet_buf);
	free(dev);
}

void close_audio_device(usb_audio_device_t* dev) {
	libusb_close(dev->dev_handle);
	free_audio_device(dev);
}

libusb_device_handle* open_device_with_port_path (
	libusb_context *ctx, uint8_t* path, size_t pathLength)
{
	struct libusb_device **devs;
	struct libusb_device *found = NULL;
	struct libusb_device *dev;
	struct libusb_device_handle *handle = NULL;
	size_t i = 0;
	int r;

	if (libusb_get_device_list(ctx, &devs) < 0) {
	    LOGD("Found no devices!");
		return NULL;
    }

	while ((dev = devs[i++]) != NULL) {
	    uint8_t devPath[7]; // 7 is the max allowed
		//int devPathLength = libusb_get_port_numbers(dev, devPath, 7);
		uint8_t bus = libusb_get_bus_number(dev);
		uint8_t addr = libusb_get_device_address(dev);
		LOGD("Device[%d] is at %d/%d", i - 1, bus, addr);
		//if (pathLength != devPathLength) continue;
		if ( !(path[0] == bus && path[1] == addr)) continue;
		found = dev;
		LOGD("Matched! Device is on port %d", libusb_get_port_number(dev));
		break;
	not_found : ; // Not 3 days back into C code and I'm using goto to break out of nested loops...
	}

	if (found) {
		r = libusb_open(found, &handle);
		if (r < 0) {
		    LOGD("Error opening device: %s\n", libusb_error_name(r));
			handle = NULL;
        }
	}

out:
	libusb_free_device_list(devs, 1);
	return handle;
}

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

    // Get the device info
    usb_audio_device_t* device = (usb_audio_device_t*) xfr->user_data;

    // Create a jbyteArray.
    int start = 0;
    jbyteArray audioByteArray = (*env)->NewByteArray(env, device->buf_len * xfr->num_iso_packets);
    for (i = 0; i < xfr->num_iso_packets; i++) {
        struct libusb_iso_packet_descriptor *pack = &xfr->iso_packet_desc[i];
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
    (*env)->CallStaticVoidMethod(env, java_callback_class,
            java_callback_method, audioByteArray, len, device->tag);
    (*env)->DeleteLocalRef(env, audioByteArray);
    if ((*env)->ExceptionCheck(env)) {
        LOGD("Exception while trying to pass sound data to java");
        return;
    }

    if (had_to_attach) {
        (*java_vm)->DetachCurrentThread(java_vm);
    }


	if (libusb_submit_transfer(xfr) < 0) {
		LOGD("error re-submitting xfr\n");
		exit(1);
	}
}

static bool begin_transfers(usb_audio_device_t* device)
{
	static struct libusb_transfer *xfr[NUM_TRANSFERS];
    // device already has packet_buf allocated
	int num_iso_pack = NUM_PACKETS;
	device->packet_buf = calloc(1, device->buf_len);
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
            return false;
        }
        libusb_fill_iso_transfer(xfr[i], device->dev_handle, device->ep_addr, device->packet_buf,
                device->buf_len, num_iso_pack, cb_xfr, device, 1000);
        libusb_set_iso_packet_lengths(xfr[i], device->buf_len/num_iso_pack);

        int ret = libusb_submit_transfer(xfr[i]);
        if (ret == -1) LOGD("IOERROR: %d", errno);
        else if (ret != 0) LOGD("ERROR: %s", libusb_error_name(ret));
    }

	LOGD("Scheduled %d transfers", NUM_TRANSFERS);
    return true;
}

uint32_t getSampleRate(usb_audio_device_t* device) {
    int rc;
    errno = 0;
    unsigned char data[3];
    rc = libusb_control_transfer(device->dev_handle,
            USB_TYPE_CLASS | USB_RECIP_ENDPOINT | USB_DIR_IN,
            GET_CUR,
            SAMPLING_FREQ_CONTROL << 8,
                device->ep_addr,
                data, 3,
                0
                );
    if (rc == -1) LOGD("IOERROR: %d", errno);
    else if (rc < 0) LOGD("ERROR: %s", libusb_error_name(rc));
    uint32_t newRate = 0;
    newRate = data[0] | (data[1] << 8) | (data[2] << 16);
    LOGD("Got sample rate of %d", newRate);
    return newRate;
}

uint32_t setSampleRate(usb_audio_device_t* device, uint32_t rate) {
    int rc;
    errno = 0;
    unsigned char data[3];
    data[0] = rate;
    data[1] = rate >> 8;
    data[2] = rate >> 16;
    rc = libusb_control_transfer(device->dev_handle,
            USB_TYPE_CLASS | USB_RECIP_ENDPOINT | USB_DIR_OUT,
            SET_CUR,
            SAMPLING_FREQ_CONTROL << 8,
                device->ep_addr,
                data, 3,
                0
                );
    if (rc == -1) LOGD("IOERROR: %d", errno);
    else if (rc < 0) LOGD("ERROR: %s", libusb_error_name(rc));
    LOGD("Set sample rate");
    return getSampleRate(device);
}

uint16_t getVolume(usb_audio_device_t* device) {
	int rc;
	uint16_t c_tr_result = 0;
	errno = 0;
	rc = libusb_control_transfer(device->dev_handle,
			USB_TYPE_CLASS | USB_RECIP_INTERFACE | USB_DIR_IN,
			GET_CUR,
			(VOLUME_CONTROL << 8) | CONTROL_INTERFACE,
			0 | (device->control_unit_id << 8),
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


JNIEXPORT bool JNICALL
Java_com_cherrydev_usbaudiodriver_UsbAudioDriver_initDriver(JNIEnv* env UNUSED, jobject foo UNUSED, jclass callbackClass, jstring methodName) {
	if (is_init) {
		LOGD("initDriver called, but it was already initted!");
		return true;
	}
    int rc = libusb_init(NULL);
    if (rc < 0) {
        LOGD("Error initializing libusb: %s\n", libusb_error_name(rc));
        return false;
    }

	java_callback_class = (*env)->NewGlobalRef(env, callbackClass);
	const char *c_method_name = (*env)->GetStringUTFChars(env, methodName, 0);
    LOGD("Looking for method name: %s", c_method_name);
	   // use your string
	java_callback_method = (*env)->GetStaticMethodID(env,
	    		java_callback_class, c_method_name, "([BII)V");

	if (!java_callback_method) {
		LOGD("Could not find method %s", c_method_name);
		(*env)->ReleaseStringUTFChars(env, methodName, c_method_name);
		(*env)->DeleteGlobalRef(env, java_callback_class);
		libusb_exit(NULL);
		return false;
	}
	(*env)->ReleaseStringUTFChars(env, methodName, c_method_name);
	is_init = true;
	LOGD("initDriver succeeded!");
	return true;
}

JNIEXPORT void JNICALL
Java_com_cherrydev_usbaudiodriver_UsbAudioDriver_closeDevice(JNIEnv* env UNUSED, jobject foo UNUSED, jlong devHandle) {
	close_audio_device( (usb_audio_device_t*) (intptr_t) devHandle);
}

JNIEXPORT jlong JNICALL
Java_com_cherrydev_usbaudiodriver_UsbAudioDriver_setupDevice(JNIEnv* env UNUSED, jobject foo UNUSED, jbyteArray portPath, jint endPoint, jint packetSize, jint interfaceNum, jint tag, jbyte controlUnitId)
{
	int rc;
	struct libusb_device_handle *devh = NULL;

	jsize portLen = (*env)->GetArrayLength(env, portPath);
	jbyte *portPathElements = (*env)->GetByteArrayElements(env, portPath, 0);
	devh = open_device_with_port_path(NULL, portPathElements, portLen);
	(*env)->ReleaseByteArrayElements(env, portPath, portPathElements, 0);


	if (!devh) {
		LOGD("Error finding USB device\n");
        libusb_exit(NULL);
        return 0;
	}

	rc = libusb_kernel_driver_active(devh, 0);
	if (rc == 1) {
		rc = libusb_detach_kernel_driver(devh, 0);
		if (rc < 0) {
			LOGD("Could not detach kernel driver for control interface: %s\n",
					libusb_error_name(rc));
			libusb_close(devh);
			libusb_exit(NULL);
			return 0;
		}
		LOGD("Detached control interface from kernel drive.");
	}

	rc = libusb_kernel_driver_active(devh, interfaceNum);
	if (rc == 1) {
		rc = libusb_detach_kernel_driver(devh, interfaceNum);
		if (rc < 0) {
			LOGD("Could not detach kernel driver for audio interface: %s\n",
					libusb_error_name(rc));
			libusb_close(devh);
			libusb_exit(NULL);
			return 0;
		}
		LOGD("Detached audio interface from kernel drive.");
	}

	rc = libusb_claim_interface(devh, interfaceNum);
	if (rc < 0) {
		LOGD("Error claiming audio interface: %s\n", libusb_error_name(rc));
        libusb_close(devh);
        libusb_exit(NULL);
        return 0;
    }

	rc = libusb_claim_interface(devh, 0);
	if (rc < 0) {
		LOGD("Error claiming control interface: %s\n", libusb_error_name(rc));
		libusb_close(devh);
		libusb_exit(NULL);
		return 0;
	}

	rc = libusb_set_interface_alt_setting(devh, interfaceNum, 1);
	if (rc < 0) {
		LOGD("Error setting alt setting: %s\n", libusb_error_name(rc));
        libusb_close(devh);
        libusb_exit(NULL);
        return 0;
	}

	usb_audio_device_t* audio_device = alloc_audio_device(packetSize, interfaceNum, devh, (uint8_t)endPoint, tag, controlUnitId);
    return (jlong)(intptr_t) audio_device;
}

JNIEXPORT jbyte JNICALL
Java_com_cherrydev_usbaudiodriver_UsbAudioDriver_getPort(JNIEnv* env UNUSED, jobject foo UNUSED, jlong audioDevice) {
    return libusb_get_port_number( libusb_get_device( ((usb_audio_device_t*)(intptr_t) audioDevice)->dev_handle));
}

JNIEXPORT bool JNICALL
Java_com_cherrydev_usbaudiodriver_UsbAudioDriver_beginReadAudio(JNIEnv* env UNUSED, jobject foo UNUSED, jlong audioDevice) {
    return begin_transfers((usb_audio_device_t*)(intptr_t) audioDevice);
}

JNIEXPORT jchar JNICALL
Java_com_cherrydev_usbaudiodriver_UsbAudioDriver_setVolume(JNIEnv* env UNUSED, jobject foo UNUSED, jlong audioDevice, jint volume) {
	int rc;
	usb_audio_device_t* device = (usb_audio_device_t*)(intptr_t) audioDevice;
	errno = 0;
	rc = libusb_control_transfer(device->dev_handle,
			USB_TYPE_CLASS | USB_RECIP_INTERFACE | USB_DIR_OUT,
			SET_CUR,
			(VOLUME_CONTROL << 8) | CONTROL_INTERFACE,
				0 | (device->control_unit_id << 8),
				(unsigned char*) &volume, sizeof(uint16_t),
				0
				);
	//LOGD("Writing new volume, result was %d and data was %d\n", rc, volume);
	if (rc == -1) LOGD("IOERROR: %d", errno);
	else if (rc < 0) LOGD("ERROR: %s", libusb_error_name(rc));
	jchar newVolume = 0;
	newVolume = getVolume(device);
	return newVolume;
}

JNIEXPORT jchar JNICALL
Java_com_cherrydev_usbaudiodriver_UsbAudioDriver_setSampleRate(JNIEnv* env UNUSED, jobject foo UNUSED, jlong audioDevice, jint rate) {
    return setSampleRate((usb_audio_device_t*)(intptr_t) audioDevice, rate);
}


JNIEXPORT bool JNICALL
Java_com_cherrydev_usbaudiodriver_UsbAudioDriver_loop(JNIEnv* env UNUSED, jobject foo UNUSED) {
	while (!stop_loop) {
		int rc = libusb_handle_events(NULL);
		if (rc != LIBUSB_SUCCESS)
			return false;
	}
	return true;
}
