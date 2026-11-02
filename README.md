# HappNG

HappNG is an Android client project for managing proxy profiles and subscriptions.

## Current status

- Android Compose app scaffold builds successfully.
- CI now builds a pinned Android binding for Xray-core from the upstream AndroidLibXrayLite project and bundles its AAR and routing data assets into the APK build.
- The native runtime packaging is an integration milestone, not proof of a working VPN connection.
- The Android `VpnService`, profile-to-Xray configuration generation, start/stop lifecycle, and real-device connection tests are still required before the app can be described as a functional VPN client.

## Build

Push to `main` or run the **Android APK** workflow manually. The workflow first builds the Xray Android runtime, then packages it with the app and uploads the debug APK.

## Upstream components and licenses

- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) (LGPL-3.0) provides the gomobile binding to Xray-core.
- Xray-core is included through that upstream binding. Review and preserve the upstream license notices when distributing builds.
