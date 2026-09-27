#!/system/bin/sh
# Warden bootstrap. Launches the privileged server via app_process.
#
#   ADB (shell, uid 2000):  adb shell sh <path>/start.sh
#   Root  (uid 0):          su -c sh <path>/start.sh
#
# app_process gives us a real ART runtime with the framework loaded, running as
# whatever identity invoked us. The server's binder is then published to the
# manager app (see BinderPublisher).

DATA=/data/local/tmp/warden
# The server classes ship inside the manager APK; resolve its path so we don't
# duplicate the dex.
APK=$(pm path app.warden 2>/dev/null | sed -n 's/^package://p' | head -n1)
[ -z "$APK" ] && APK="$DATA/server.dex"

mkdir -p "$DATA"
export CLASSPATH="$APK"

echo "warden: starting server as uid $(id -u) from $APK"
exec app_process /system/bin --nice-name=warden_server app.warden.server.Starter "$DATA"
