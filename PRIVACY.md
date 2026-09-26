# ClearPDF Privacy

ClearPDF is designed to be an offline-first PDF utility for Android.

## What ClearPDF does not do

- It does not require an account.
- It does not show ads.
- It does not include analytics, telemetry, or crash-reporting services.
- It does not intentionally upload PDFs or extracted content to a server.
- The Google Play build does not request Android's `INTERNET` permission.

## Build flavors and the optional Office engine

ClearPDF ships in two flavors:

- **play** (Google Play): no `INTERNET` permission. The optional Office engine (powered by
  LibreOffice) is delivered by Google Play itself as an on-demand module, only when you tap
  *Download* in Settings.
- **foss** (GitHub / sideload builds): declares `INTERNET` for exactly one purpose — downloading that
  same optional engine when you ask for it. The download goes to a fixed GitHub release URL, sends no
  document or personal data, and is installed only if its size and SHA-256 checksum match values
  pinned in the app. Nothing is downloaded unless you start it, and you can delete the engine at any
  time in Settings.

In both flavors your documents never leave the device: conversion runs locally in an isolated app
process, and the app's WebViews (used to lay out .docx and HTML) block all network requests and opt
out of Safe Browsing lookups and WebView metrics.

## Files and permissions

Files are opened or saved only when you choose them through Android's document picker or the app's export flow. Recent-file entries are stored locally on the device and can be removed from the home screen.

## On-device scanning and text selection

Scanning and text-selection features use the on-device Google ML Kit components listed in [`NOTICE`](NOTICE). ClearPDF does not intentionally send their input to a ClearPDF server. This dependency is disclosed so contributors can make informed decisions about builds and distribution.

## Reporting a concern

Please do not attach private PDFs or personal information to public issues. For a suspected security or privacy vulnerability, follow [`SECURITY.md`](SECURITY.md).
