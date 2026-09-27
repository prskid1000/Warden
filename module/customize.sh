# Warden Magisk module install hook.
ui_print "- Installing Warden Zygisk module"
mkdir -p /data/adb/warden
# The su shim the companion bind-mounts into rooted-list apps.
if [ -f "$MODPATH/warden/su" ]; then
  cp -f "$MODPATH/warden/su" /data/adb/warden/su
  chmod 0755 /data/adb/warden/su
fi
ui_print "- Rooted list lives at /data/adb/warden/rooted.list (managed by the app)"
set_perm_recursive "$MODPATH" 0 0 0755 0644
