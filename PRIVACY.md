# ClearPDF privacy notes

ClearPDF is an offline-capable Android document app. It has no account system, in-app advertising, analytics SDK, or crash-reporting service in the app code. Document operations run on the device; ClearPDF does not provide a server for uploading user files or extracted text.

## Network use

Network access is limited to features that need it:

- **Optional Office engine:** In the GitHub/FOSS build, you can choose to download the LibreOffice-based engine from a pinned GitHub release in Settings. ClearPDF checks the expected archive size and SHA-256 before installing it. The engine converts documents locally in an isolated app process.
- **Web address to PDF:** The FOSS build can load a web address you enter and render the page as a PDF. This sends normal requests to that website and any page resources it loads. The request goes to the address you chose, not to a ClearPDF document-processing server.
- **Google Play services components:** The scanner is provided by Google Play services. Background removal uses ML Kit subject segmentation, whose model may be delivered by Google Play services. Google manages those service/model downloads; document processing remains on-device.
- **Play distribution:** The optional Office engine is delivered through Google Play's on-demand feature delivery when requested. The app's main manifest does not declare the <code>INTERNET</code> permission; bundled libraries may contribute permissions during manifest merging. See <a href="NOTICE">NOTICE</a> and the dependency manifests for details.

Converting local HTML, rendering DOCX content, OCR, spreadsheet editing, image tools, and PDF operations use local content. The WebViews used for local HTML and DOCX rendering block external network loads; URL-to-PDF intentionally loads the address you enter.

## Files and permissions

Files are opened and saved through Android's document picker or ClearPDF's export flow. Recent-file entries are stored locally and can be removed from the home screen. ClearPDF does not intentionally send selected documents or their extracted contents to a ClearPDF-controlled server.

Android permissions are requested for features that need them, such as camera access for scanning. Device and Android-version availability can affect individual features.

## Scanning and OCR

Paper scanning uses Google ML Kit Document Scanner through Google Play services. Background removal uses ML Kit subject segmentation; Google Play services may provide its model. OCR for scanned or image-only PDF pages uses bundled on-device ML Kit text recognition with a Tesseract fallback. These components process content on-device and do not send document contents to a ClearPDF server.

## Reporting a concern

Do not attach private documents, personal information, access tokens, or signing keys to public issues. For a suspected vulnerability, use the private reporting link in <a href="SECURITY.md">SECURITY.md</a>.
