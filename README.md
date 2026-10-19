# Relay Control (Android, Jetpack Compose)
Open this folder in Android Studio (Koala+), let Gradle sync, run on a device on the same Wi-Fi as the relay controller.
- Scan: probes <your /24>.250 first, then .1-.254 and 192.168.4.1, GET /api/status (700 ms timeout, 48 in parallel). Manual IP also supported.
- Writes are verified against /api/status; All ON is staggered (500 ms) and confirmed first; names sanitized to [A-Za-z0-9 _-].
- Cleartext HTTP is enabled in the manifest (device has no TLS).
