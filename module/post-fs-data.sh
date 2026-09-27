#!/system/bin/sh
# Warden — allow apps to look up the broker service under enforcing SELinux.
#
# The server registers a binder named "warden" in ServiceManager. By default an
# untrusted_app is not permitted to `find` a custom service_manager entry
# (observed on API 36: avc denied { find } name=warden tclass=service_manager).
# magiskpolicy injects a live rule so the manager and granted apps can resolve
# it. Without this the root path requires the binder-handoff instead.
magiskpolicy --live "allow untrusted_app default_android_service service_manager find" 2>/dev/null
magiskpolicy --live "allow isolated_app default_android_service service_manager find" 2>/dev/null
