# ATHEX Android MVP v1.1

## Added in v1.1
- Smart one-time reminders scheduled through Android WorkManager
- Notification channel and Android 13+ notification permission request
- Hindi-first speech recognition and Hindi text-to-speech fallback
- Offline command responses for expense totals, tasks and reminders
- Local persistence for expenses, tasks and reminder list

## Open and run
1. Extract this ZIP.
2. Open the `ATHEX_Android_V2` folder in Android Studio.
3. Install Android SDK Platform 35 if prompted and sync Gradle.
4. Run on an Android phone (Android 8.0/API 26+).
5. Allow microphone and notification permissions when requested.

## Important honest limitations
- This is project source code, not a compiled or signed APK.
- It has not been built on a real Android SDK in this environment; compile/device testing is still required.
- Reminders are one-time notification reminders. WorkManager can delay delivery due to Doze/battery restrictions; they are not guaranteed exact alarms.
- Voice recognition depends on the Android speech service and network/device language support. The app is not always listening in the background.
- The assistant currently uses local command rules, not a connected generative AI model. Full AI chat requires an API/backend and secure key handling.
- Finance data is manual and stored locally on the device. No bank account connection or automatic transaction import exists.
- Uninstalling app or clearing app data can remove locally stored records.
