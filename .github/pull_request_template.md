## What this changes

<!-- What the change does and why. Link the issue it closes: "Closes #123". -->

## How it was checked

<!-- The device and Android version you tried it on, or the tests you ran. -->

## Checklist

- [ ] The change does one thing (unrelated fixes go in their own pull request)
- [ ] `cargo test -p artefact-mobile` passes if the engine was touched
- [ ] The app builds (`./gradlew assembleDebug -Partefact.abis=arm64-v8a`) and was tried on a device or emulator
- [ ] New text is in `res/values/strings.xml`, not hard-coded
- [ ] Screenshots or a short recording are attached for anything visible
- [ ] I agree to license this contribution under the project's licences (MIT / Apache-2.0)
