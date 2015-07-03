LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libusb1.0
LOCAL_SRC_FILES := libusb-1.0/lib/$(TARGET_ARCH_ABI)/libusb1.0.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/libusb-1.0/include/libusb-1.0
LOCAL_EXPORT_LDLIBS := -llog
include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE	:= usbaudio
LOCAL_SHARED_LIBRARIES := libusb1.0
LOCAL_LDLIBS := -llog
LOCAL_CFLAGS := -std=c99
LOCAL_SRC_FILES := usb_audio.c
include $(BUILD_SHARED_LIBRARY)

