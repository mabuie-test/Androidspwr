# Device Manager Suite Monorepo

This repository hosts both the Android mobile application and the web platform
(backend API and web frontend). The code is now grouped by platform to make it
simpler to work on each component independently.

## Repository structure

- `android/` – Android Studio/AIDE project for the Device Manager mobile app.
  Open this folder in Android Studio (or AIDE) to build and run the app.
- `web/` – Source code for the web platform, including the Node.js backend and
  the static frontend assets. See the platform specific README for details on
  running each part.

## Getting started

### Android app
1. Open the `android/` directory in Android Studio or import it as a Gradle
   project.
2. Configure any required signing keys and `local.properties` as needed for
   your environment.
3. Use the standard Android Studio build and run actions to install the
   application on a device or emulator.

### Web platform
See [`web/README.md`](web/README.md) for setup instructions covering both the
backend API and the static frontend.

## Contributing

1. Fork the repository and create a new branch for your work.
2. Make your changes and ensure both the Android and web projects continue to
   build.
3. Submit a pull request describing your updates.
