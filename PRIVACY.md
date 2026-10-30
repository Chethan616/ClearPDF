# Privacy Policy

**Effective date:** October 30, 2026
**App:** ClearPDF (Android)
**Developer:** Chethan ("we", "us", "our") — contact: chethankrishna2022@gmail.com

This Privacy Policy explains how ClearPDF handles information when you use the app, and applies to every distribution of ClearPDF (Google Play, GitHub releases, F-Droid, and any other channel we publish to). ClearPDF is offered to users worldwide, in every country where it's available for download. This is a plain-English policy written to be accurate about a genuinely offline-first app; it is not a substitute for independent legal advice.

## Summary

ClearPDF has no account system, no in-app advertising, no analytics SDK, and no crash-reporting service in the app's own code. Your documents are processed on your device. We do not operate a server that receives your files, your extracted text, or your document contents. A small number of features make network requests, described below, and none of them send your documents to us.

## Information we collect

**We do not collect personal information ourselves.** ClearPDF has no sign-up, no user profile, and no ClearPDF-operated backend. We do not know who you are, and nothing you open or create in the app is transmitted to us.

Information that stays entirely on your device includes:

- The documents, images, and files you open, edit, or create.
- Recent-file entries (name, size, last position), stored locally so you can resume reading. You can remove these from the home screen at any time, and they are deleted when you uninstall the app or clear its storage.
- App preferences (theme, Liquid Glass style, motion settings, language, etc.), stored locally.

## How network access is used

ClearPDF's main app does not declare the `INTERNET` permission; only the specific features below make network requests, and only when you use them:

- **Optional Office engine.** You can choose, in Settings, to download a LibreOffice-based rendering engine. On GitHub/F-Droid builds this is fetched from a pinned GitHub release and checked against an expected file size and SHA-256 hash before installing. On the Play build it is delivered through Google Play's on-demand feature delivery. The engine converts documents locally, in an isolated process on your device; no document content is sent anywhere during this.
- **Web address to PDF.** If you enter a web address to convert to PDF (GitHub/F-Droid builds only), ClearPDF requests that address and its page resources, the same way a browser would. This request goes to the site you chose — not to any ClearPDF-operated server.
- **Google Play services.** Paper scanning uses Google ML Kit Document Scanner, provided by Google Play services. Automatic background removal in the image editor uses ML Kit subject segmentation, whose model may be delivered by Google Play services. These run through Google's components under Google's own terms (see "Third-party services" below); ClearPDF does not separately collect what passes through them.
- **App updates and, on the Play Store build, purchases.** Google Play itself contacts Google's servers to check for updates and, if you buy anything in the app, to process that purchase. See "Payments" below.

Everything else — reading and annotating PDFs, OCR, spreadsheet editing, image editing, local DOCX/HTML rendering, PDF creation from local content — works entirely offline and does not touch the network. The WebViews used for local HTML and DOCX rendering block external network loads.

## Third-party services

ClearPDF uses the following third-party components, each under its own privacy terms:

- **Google Play Services / ML Kit** (document scanning, OCR fallback delivery, subject segmentation) — see [Google's Privacy Policy](https://policies.google.com/privacy) and the [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms).
- **Google Play Store** (distribution, updates, and — on the Play build — in-app purchases) — see [Google Play's Terms of Service](https://play.google/play-terms/) and, if applicable, [Google Payments' Privacy Notice](https://payments.google.com/payments/apis-secure/get_legal_document?ldo=0&ldt=privacynotice).
- **GitHub** (hosting the optional Office engine download and the app's source code, for GitHub/F-Droid builds) — see [GitHub's Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

We do not sell your information, because we never receive it in the first place.

## Permissions

Android permissions (camera for scanning, storage/photo access for opening and saving files, etc.) are requested only when a feature that needs them is used, and availability can vary by device and Android version. You can review and revoke permissions at any time in Android Settings → Apps → ClearPDF → Permissions.

## Payments (Play Store build only)

If you purchase anything in ClearPDF through the Google Play Store, that purchase is processed entirely by Google Play Billing. We do not receive or store your payment card details; Google handles billing, receipts, and refunds under its own [refund policy](https://support.google.com/googleplay/answer/2479637). We only learn that an entitlement was purchased, not your payment information.

## Children's privacy

ClearPDF is not directed at children under 13 (or the equivalent minimum age in your country), and we do not knowingly collect personal information from anyone, including children, because we do not collect personal information from any user. If you believe a child has provided us personal information, contact us at the address below and we will look into it — though as described above, there is normally nothing for us to hold.

## Your rights

Because ClearPDF doesn't collect or store personal information on a server, there is generally nothing held by us to access, correct, export, or delete under regulations such as the GDPR or CCPA. Data that exists — your documents, recent-file entries, and preferences — lives on your device under your control: delete it by removing the recent entry, clearing the app's storage, or uninstalling the app.

## Data retention

We do not retain your data because we do not receive it. On-device data persists until you remove it or uninstall the app.

## Security

Document processing happens locally in the app's own sandboxed storage, protected by Android's standard app-sandboxing. No transmission to a ClearPDF server means there is no ClearPDF-side data breach surface for your documents. The optional Office engine download is integrity-checked (SHA-256) before use.

## Changes to this policy

We may update this policy as the app changes — for example, if a new feature adds a network call. We'll update the effective date above and, for material changes, note them in the app's release notes. Continued use of the app after an update means you accept the revised policy.

## Contact us

Questions about this policy, or a privacy concern: chethankrishna2022@gmail.com. For a security vulnerability specifically, use the private reporting path in [SECURITY.md](SECURITY.md) instead of a public issue.

---

See also: [Terms of Service](TERMS.md) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Security policy](SECURITY.md)
