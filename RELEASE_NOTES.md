# SoAI Connect Release Notes

## 1.0.1

SoAI Connect 1.0.1 fixes connection discovery, background notification handling, and Wake-on-LAN error reporting.

### Connection discovery

- HTTP and HTTPS attempts each receive their own timeout budget. A slow or stalled first attempt no longer prevents discovery from trying the other protocol, including when a port is entered explicitly.
- Explicit HTTP or HTTPS remains the preferred first attempt. Existing certificate-trust decisions and cleartext warnings remain in effect.
- When discovery finds multiple servers, the error lists their endpoints so you can choose the intended server.

### Notifications

- The background listener checks whether it is still allowed to run while a WebSocket connection remains open, rather than waiting for that connection to end. Checks run every 30 seconds and cover notification settings, permissions, and session availability.
- Reconnection delays include jitter and capped backoff, with the delay reset after a sustained connection.
- Improved listener shutdown and handling of policy changes and revoked authentication.

### Wake-on-LAN

- Delivery failures are no longer reported as an invalid broadcast address. Invalid settings and network delivery failures produce distinct feedback.
- The port field uses digits accepted by its parser regardless of the device's language settings.
- Updated localized messages for discovery and Wake-on-LAN failures.

### Installation and upgrade

- Android 8.0 (API 26) or newer is required. Distribution remains by sideloading from the Releases section of the [SoAI Connect repository](https://github.com/GetSoAI/SoAI_Connect).
- Download `SoAI-Connect-1.0.1-android.apk` and its matching `.sha256` sidecar. APK filenames now use the client version without a build timestamp.
- Verify the checksum and signing-certificate fingerprint using the instructions in this repository's `README.md` before installation. APK signatures use schemes v2 and v3.
- To update an existing installation, install the APK over the app using the same signing identity. Do not uninstall or clear application data; saved servers, trusted certificate pins, preferences, and connection state must be retained.
- After updating, check your saved server connection, certificate trust, notification delivery, and Wake-on-LAN if you use it. Background notifications still depend on Android permissions and battery-management settings.
