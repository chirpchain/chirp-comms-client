#define USB_TYPE_CLASS (0x01 << 5)
#define USB_RECIP_INTERFACE 0x01
#define USB_RECIP_ENDPOINT 0x02
#define USB_DIR_IN 0x80
#define USB_DIR_OUT 0
#define GET_CUR  0x81
#define GET_MIN  0x82
#define GET_MAX  0x83
#define GET_RES  0x84
#define SET_CUR  0x01
#define VOLUME_CONTROL  0x02
#define SAMPLING_FREQ_CONTROL 0x01
#define CONTROL_INTERFACE 0x00

#include <android/log.h>
#define LOGD(...) \
    __android_log_print(ANDROID_LOG_DEBUG, "UsbAudioNative", __VA_ARGS__)
