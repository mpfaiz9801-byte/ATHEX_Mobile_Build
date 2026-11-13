# ATHEX - Mobile Cloud Build

This source package includes a GitHub Actions workflow at `.github/workflows/android-apk.yml`.

## Build from an Android phone
1. Download and extract this ZIP on your phone.
2. Create a new GitHub repository (Private is recommended).
3. Upload the *contents* of the extracted `ATHEX_Mobile_Build` folder to the repository root. Keep hidden `.github` folder and its workflow file.
4. Open the repository's Actions tab and enable workflows if GitHub asks.
5. Select **Build ATHEX APK** > **Run workflow**.
6. Wait for the run to finish. If green, open the run and download **ATHEX-debug-APK** under Artifacts.
7. Extract the downloaded artifact ZIP and install the APK. Android may ask permission to install unknown apps; allow only for the browser/file manager you used.

This builds a debug APK, not a Play Store release. It has not been verified on a physical Android device. The GitHub Actions build is the first real compiler check; if it fails, inspect the build log before installing.
