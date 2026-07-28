# ClearPDF Privacy

ClearPDF is designed to be an offline-first PDF utility for Android.

## What ClearPDF does not do

- It does not require an account.
- It does not show ads.
- It does not include analytics, telemetry, or crash-reporting services.
- It does not intentionally upload PDFs or extracted content to a server.
- The app does not request Android's `INTERNET` permission.

## Files and permissions

Files are opened or saved only when you choose them through Android's document picker or the app's export flow. Recent-file entries are stored locally on the device and can be removed from the home screen.

## On-device scanning and text selection

Scanning and text-selection features use the on-device Google ML Kit components listed in [`NOTICE`](NOTICE). ClearPDF does not intentionally send their input to a ClearPDF server. This dependency is disclosed so contributors can make informed decisions about builds and distribution.

## Reporting a concern

Please do not attach private PDFs or personal information to public issues. For a suspected security or privacy vulnerability, follow [`SECURITY.md`](SECURITY.md).
