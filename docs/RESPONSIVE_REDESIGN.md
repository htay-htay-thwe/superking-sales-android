# Sales workspace redesign

## Reference and scope

The supplied `superking-sales-mobile-screenshots` (390 × 844) and
`superking-sales-large-screenshots` (1440 × 900) are the visual source of truth.
Those reference files are unchanged. This is a native Views implementation, not a WebView.

Implemented presentation:

- Orange accents, navy text, muted labels, white bordered panels, compact section headings and status labels.
- Phone: company/logo/account header, explicit back affordance and the existing five primary bottom destinations.
- Windows at least 600 dp wide: bounded, centered app canvas, top navigation including My stock,
  account name and connection status, wider content gutters, responsive summary and form columns.
- Dashboard: cash/sales/stock metrics, create-sale action, recent transactions, stock and receiving sections.
- Trip: current-trip identity, financial metrics, payment methods, sales/expenses and guarded ending action.
- Stock: balance metrics, current/pending/history selectors, receiving preview and inventory quantities.
- Sales: gross/cash/credit/unit summaries, inline search, advanced filters, status and draft/printing commands.
- Cash: active trip, custody/available/confirmed summaries, cash breakdown, returns/ledger and history scope.
- Customers: inline search, account/credit details and eligible collection actions; two-column creation form on wider windows.
- Sale detail/receiving: transaction identity and status, grouped products, audit and explicit commands.
- Sale wizard: four-step progress, customer selection, payment tiles, responsive quantity fields, server-total review.
- Profile: personal/security panels, appearance preferences and preserved printer/GPS controls.
- Sign-in: centered, bounded native form with brand asset and password visibility control.

Native adjustments are intentional: 48 dp controls, scalable text, accessible orange text/button colour
(darker than the screenshot's decorative orange), explicit confirmation dialogs and separate advanced filters.
Panel grids remeasure their existing children; resizing does not recreate or clear inputs.
Long lists still use RecyclerView and immutable DiffUtil content.

Android guidance consulted:
[adaptive Views layouts](https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views)
and [accessible touch targets](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views).

## State and business safety

Existing session, idempotency, authoritative server pricing, GPS capture, validation, credit eligibility,
trip restrictions, printing and mutation confirmation handlers are retained.
Pull-to-refresh remains available on every screen; it does not submit transactions or clear drafts.
The stock screen now loads pending receivings alongside inventory using existing read-only endpoints.
The regular APK still connects to the live HTTPS server. Instrumentation uses a separate mock repository
and a test-only connectivity override, so localhost business tests do not depend on external internet probes.
An explicit offline-save test verifies that no mutation is sent and the draft is retained.

## Reproducible visual review

`ResponsiveLayoutTest` captures the principal destinations and checks navigation at the current window width.
It also checks that grid resizing preserves the same input view and its text.
Run on a dedicated emulator at phone and wide dimensions, not on a user's device with business data:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
adb -s <emulator> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell am instrument -w -r -e screenshots phone -e class com.example.superkingsale.ResponsiveLayoutTest com.example.superkingsale.test/com.example.superkingsale.SalesTestRunner
```

Captures are written under the test app's external-files `redesign/<screenshots argument>` directory.
Reset emulator size/density overrides after testing. Screenshots with OS dialogs or incomplete loading
are not valid visual acceptance evidence, even if view-hierarchy assertions pass.

## Build verification — 2026-09-18

- Debug APK, Android test APK and unsigned release APK built successfully.
- 27 JVM tests passed, including new presentation tests for draft commands, credit eligibility,
  receiving links and cash settlement figures.
- Android lint: 0 errors, 54 warnings (including existing dependency/style advisories and new layout advisories).
- Expanded Myanmar dictionary audit: 2,047 entries, no unmapped static UI/error calls detected.
  This is an automated coverage check, not human proofreading.
- Debug APK SHA-256: `2211E89AF9212A3D1D0D90D964D23649C24CC54C164061EB8B95FEDD49C07CFA`.
- The updated debug APK was installed on the connected Android phone without clearing its data.
  The phone was locked during deployment, so installation alone is not a visual/device acceptance claim.
- No live sales, collections, stock or cash records were created or changed for this redesign.

## Acceptance boundary

This redesign does not constitute physical GPS, actual thermal-printer or human Myanmar proofreading acceptance.
Those production checks remain as documented in `HARDWARE_AND_MYANMAR.md`.
