#!/system/bin/sh

# First-boot Device Owner provisioning for rooted/Magisk production images.
# This runs as root from Magisk and does not require ADB. Android still allows
# provisioning only while the primary user is not fully set up.
PKG="com.o0ai.control"
ADMIN="${PKG}/.admin.ControlAdminReceiver"
LOG_DIR="/data/adb/o0ai-control"
LOG_FILE="${LOG_DIR}/provision.log"

mkdir -p "${LOG_DIR}"
chmod 700 "${LOG_DIR}"
exec >>"${LOG_FILE}" 2>&1

echo "[$(date '+%F %T')] auto-provision started"

# Do not interfere with another existing owner.
if dumpsys device_policy | grep -E "Device Owner:|Device Owner component" | grep -vq "null"; then
    echo "[$(date '+%F %T')] a device owner already exists; skipping"
    exit 0
fi

attempt=0
while [ "${attempt}" -lt 90 ]; do
    if pm path "${PKG}" >/dev/null 2>&1; then
        # Magisk service.sh is UID 0. Run dpm as Android's shell UID so the
        # framework accepts the same provisioning path as the setup tool.
        if su -u 2000 -c "dpm set-device-owner ${ADMIN}"; then
            echo "[$(date '+%F %T')] device owner provisioned"
            exit 0
        fi
        if dumpsys device_policy | grep -q "${PKG}"; then
            echo "[$(date '+%F %T')] device owner detected after provisioning"
            exit 0
        fi
    fi
    attempt=$((attempt + 1))
    sleep 2
done

echo "[$(date '+%F %T')] provisioning skipped or failed; setup may already be complete"
exit 0
