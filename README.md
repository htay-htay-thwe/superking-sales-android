# Super King Sales — Android

Native Kotlin sales-representative client for the sales portal in `D:\xampp\htdocs\inventory`.
The normal app connects to **https://www.superkingmyanmar.com/public/**; there is no demo-data mode or embedded website.

## Open and run

Open this folder in Android Studio, sync Gradle, select a device, and run `app`.
Use an active **sales-representative** account from the existing server.
New sales require an operating trip, assigned customer/product availability, and Android location permission.
The application does not create an account or change backend configuration.

- JDK 17, Gradle wrapper 9.7.1, Android Gradle Plugin 9.4.0 with built-in Kotlin.
- Compile/target SDK 37; minimum SDK 23 (Android 6).
- Native Material Views, XML/View Binding shell, single Activity with Navigation Fragments.
- Screen ViewModels, StateFlow, SavedStateHandle, lifecycle-aware collectors, Retrofit/OkHttp.

Architecture follows the [Android Views architecture recommendations](https://developer.android.com/topic/architecture/views/recommendations-views)
and [ViewModel guidance](https://developer.android.com/topic/libraries/architecture/views/viewmodel), retaining this project's XML/Views architecture.

## Features

Superking branding includes adaptive/round/legacy launcher icons, an Android 13+ themed eagle icon,
light/dark native splash screens and the sign-in logo. See [branding assets and provenance](docs/APP_BRANDING.md).

The five primary phone tabs remain **Home → Trip → New sale → Sales → Cash**.
The supplied phone/large-screen designs are implemented with orange-accent panels, responsive summary/form grids
and top navigation (including My stock) on wider windows. See [responsive redesign](docs/RESPONSIVE_REDESIGN.md).
The sale wizard retains **Information → Products → Quantity → Review & submit**, then server-backed draft and posting confirmation.

Includes stock/receiving, customers and credit collections, trip expenses/ending, cash returns and ledger,
draft editing/deletion/posting, sale detail/map/invoice printing, profile/password, and appearance settings.
All main destinations support pull-to-refresh at the top of their content, replacing the persistent Refresh button.
Forms retain unsaved input and sale drafts; refresh only reloads data. Sign-in refresh retries the session check,
not credential submission. Saves/posting block refresh, and failed refreshes retain existing content with a retry action.
TalkBack exposes a Refresh accessibility action. GPS capture and printer actions remain explicit, not swipe-triggered.
The existing Views UI uses [AndroidX SwipeRefreshLayout 1.2.0](https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout).
Hardware support includes a foreground GPS diagnostic, Bluetooth Classic/SPP, USB/OTG and Wi-Fi/TCP ESC/POS
connection selectors, 50/58/80 mm raster receipts and exact-width PDF export with Myanmar shaping.
See [hardware and Myanmar notes](docs/HARDWARE_AND_MYANMAR.md) for compatibility, tests and remaining physical acceptance.
See [feature and validation checklist](docs/SALES_APP_PARITY.md) for mappings, safeguards, and acceptance-test limitations.

## Build and test

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`; configure your own release signing before distribution.
No signing secrets are included.

With an emulator connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The custom instrumentation runner replaces the application repository with a localhost MockWebServer fixture.
These default tests **never send business mutations to the live server**. Only debug networking permits localhost cleartext;
the normal application uses HTTPS, and release builds forbid cleartext.

## State and security

- Session cookies and local sale drafts are encrypted with an Android Keystore-backed AES-GCM key. Passwords are never persisted.
- Laravel Sanctum cookie/CSRF authentication is used, matching the source portal; no invented bearer-token endpoints.
- Draft and filter state survive recreation; local sale drafts also survive process restart. Logout/session expiry clears private local data.
- Mutation controls block repeated taps. Supported commands retain idempotency keys across interrupted requests.
- An uncertain new-sale save locks the original request body for safe retry instead of silently creating a second sale.
- No automatic offline sales or background mutation queue. Connectivity errors and retry states remain explicit.
- Printed HTML escapes business data; the WebView is only a JavaScript-disabled Android print renderer.

## Live acceptance

With the owner's explicit testing/CRUD permission, the full live test-trip lifecycle passed on 2026-09-18:
stock issue/receiving, customer creation, cash/banking/credit sales, collections, expenses, cash return/cancellation,
office confirmation, stock return and trip completion. Final representative stock, FOC, cash and test-customer debt were zero.
Native rotation, force-stop draft recovery and offline-save protection were also exercised.

See [live acceptance report](docs/LIVE_ACCEPTANCE_REPORT.md) for retained test records, exact balances,
native-versus-API coverage, discovered fixes and remaining physical-device/printer acceptance.
Live tests require a separate `-PliveAcceptance=true` build, explicit `allowLiveWrites=YES`, credentials supplied
at runtime, and a selected phase. Never enable this runner for routine tests or an uncontrolled account.
