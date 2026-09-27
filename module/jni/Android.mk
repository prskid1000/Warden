LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := warden
LOCAL_SRC_FILES := warden_module.cpp
LOCAL_LDLIBS    := -llog
LOCAL_CPPFLAGS  := -std=c++17 -fno-exceptions -fno-rtti
include $(BUILD_SHARED_LIBRARY)
