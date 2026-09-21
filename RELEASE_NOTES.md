# SoAI Connect Release Notes

## 1.0.2

SoAI Connect 1.0.2 makes Android notifications and connection checks more dependable, preserves the WebUI while a window changes size, and cleans up temporary camera photos.

### Improvements

- Android notifications now have one native delivery path. This prevents duplicate alerts and prevents notifications that arrived through the former WebUI path from remaining after you sign out, change server, or enable Incognito Mode. The listener can also resume after the device restarts or the app is updated when notifications remain enabled.
- Connection discovery distinguishes a reachable SoAI server that is starting or stopping from a non-SoAI endpoint. Slow HTTPS discovery responses, including those using a certificate that needs approval, receive enough time to complete.
- The notification listener checks whether it may keep running every 30 seconds while connected, rather than repeatedly checking during a healthy connection.

### Fixes

- Android notifications for vault secret prompts now show "Secret required" instead of the generic "New SoAI notification" text. Alerts for OpenAI quota exhaustion, API rate limiting, backup failures, low disk space, messaging accounts that need attention or have recovered, plugin circuit breakers, and login protection also show their own title and message in English and the app's 25 localized language variants.
- On Android 12 and newer, Android can refuse to start the background notification listener, for example when it restarts the listener while SoAI Connect is in the background, and the app crashed when that happened. The listener now stops instead and starts again the next time you open SoAI Connect.
- Resizing SoAI Connect in split-screen or another multi-window mode no longer reloads the WebUI, so the page you are on and any text you have not sent stay in place.
- Fixed camera photos taken for WebUI uploads never being deleted from the device. SoAI Connect now removes photos older than 24 hours when you take a new one. To delete photos left by earlier versions, go to Android Settings > Apps > SoAI Connect > Storage and tap Clear cache. Do not tap Clear storage, which also removes your saved servers and settings.
- Opening or closing the app's notification service no longer blocks the screen during sign-out or server changes. Android capture-permission denials also no longer show an unrelated error message.

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
