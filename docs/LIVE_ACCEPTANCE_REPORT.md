# Live business-process acceptance — 18 September 2026

This report preserves the earlier business-process run. Subsequent direct printer support, foreground GPS changes and their separate acceptance limits are documented in [hardware and Myanmar readiness](HARDWARE_AND_MYANMAR.md).

## Outcome and scope

The approved Way8 test representative completed a full live-server trip lifecycle.
Trip **TRP-000009** is completed, with zero representative paid/FOC stock, zero cash held,
zero pending cash submissions, zero test-customer debt and zero closing stock/cash variances.
The source sales navigation and four-step sale flow remain native Android screens.

The owner explicitly authorized CRUD on this testing server. No Laravel source was modified.
Office setup/confirmation was exercised through authenticated admin API calls, not the admin browser UI.
The in-app browser skill was attempted but its browser runtime could not initialize in this environment.
All Android interaction used the dedicated API 37 emulator; no physical-device GPS/printing is claimed.

## Live coverage

| Phase | Verified behavior | Execution |
| --- | --- | --- |
| Sign-in and navigation | Representative login; Home, Trip, New Sale, Sales, Cash, Stock, Customers and Profile | Native Android UI + live API |
| Stock and trip setup | Import, trip creation, issue/dispatch, receive-all confirmation, operation start | Admin API; native receiving |
| New customer and sale | Cash-only customer creation, four steps, pack conversion, 1.5% discount, item promotion, cashback, server draft confirmation and posting | Native Android UI |
| Draft lifecycle | Create, edit, delete; posted deletion rejected; draft prevents ending | App repository + live API; native delete |
| Stock and credit limits | Paid overstock, FOC overstock and credit-disabled posting rejected | Live API; rejected drafts deleted |
| Payments | Cash, banking and credit sales; cash and banking credit collections | App repository + live API; native cash collection |
| Financial forms | Collection dialog survives rotation; expense; cash submission and cancellation | Native Android UI |
| Reconciliation | Payment-specific cash behavior, customer debt, paid/FOC stock, history totals, ledger, idempotent create replay and office sale void | Live API assertions |
| Profile/security | Save unchanged profile, reject incorrect current password, password workflow, encrypted session restoration | Native UI + real repository |
| Durable state | Quantity rotation, financial-dialog rotation, encrypted sale draft after actual force-stop, offline save blocked with input retained, connectivity recovery | Native UI/emulator |
| Trip close | Begin ending; reject new sales/collections; refuse incomplete closing; return all stock; final cash submission, office confirmation and complete trip | Native ending/cash; admin API |
| Appearance | Switch Myanmar/English, dark theme, larger fonts and compact density; restore defaults | Native Android UI |
| Printing | Launch each of the five paper choices, return from system print UI | Native UI; actual custom thermal media depends on the print service |

The password workflow set the supplied testing password to the **same value**; credentials were not changed or embedded in app code.
Sale creation used explicitly simulated emulator GPS near Mandalay; its notes identify it as QA, not an actual visit.
The app itself uses Android location APIs and does not inject these coordinates.

## Retained test records

All test documents use **ANDROID-QA-20260918-105339** where a notes/title field is available.

| Record | Identifier / outcome |
| --- | --- |
| Representative | Way8, representative ID 10, user ID 15 |
| Customer | ID 15 / CUS-000015, `ANDROID-QA-20260918-105339 TEST SHOP`; debt zero |
| Stock import | ID 3, 110 base units of existing product ID 3 into Mandalay Warehouse ID 2 |
| Trip | ID 9 / TRP-000009, completed |
| Representative issue | ID 12, 100 paid + 10 FOC base units received |
| Native cash invoice | ID 18 / SAL-000018, 539,102 MMK posted |
| Credit invoices | IDs 23 and 25, 45,622 and 22,811 MMK posted; fully collected |
| Banking invoice | ID 24, 22,811 MMK posted |
| Office-void check | ID 27, voided; stock/cash restored |
| Cancelled cash submissions | IDs 13 and 14, 100 and 101 MMK |
| Final cash submission | ID 15, 584,724 MMK, office confirmed |
| Representative stock return | ID 13, 72 paid + 9 FOC returned to warehouse |
| Deleted QA drafts | Sale IDs 19, 20, 21, 22 and 26; deletion was part of authorized CRUD testing |

The posted invoices, customer, stock movements, expenses, collections, cancelled submissions and completed trip remain
for inspection. Deleted draft records are not directly recoverable through the app; associated audit entries may remain.
The QA customer remains active and was office-enabled for credit during testing. Its current debt is zero.
The warehouse retains 81 returned test-import units; this is not a rollback to the server's original inventory.
Do not carry this QA data into production without the owner's cleanup/data-reset decision.

## Financial and stock reconciliation

- Imported 110 base units; issued 100 paid + 10 FOC.
- Net consumed: 28 paid + 1 FOC. Returned: 72 paid + 9 FOC.
- Native discounted invoice: 547,464 − 8,212 − 100 − 50 = **539,102 MMK**.
- Total posted sales excluding voided invoice: **630,346 MMK**.
- Cash received: **584,724 MMK**. Banking received: **45,622 MMK**.
- Two expenses total **130 MMK**. As in the source business rules, recording expenses does not reduce cash held.
- Final confirmed cash return: **584,724 MMK**. Final representative cash and customer outstanding credit: **0**.

## Fixes found during acceptance

- Sale wizard now scrolls to the beginning on step changes; review heading no longer opens offscreen.
- Stock labels distinguish base quantities from selling-pack quantities.
- Profile displays assigned regions as read-only, matching server authority.
- Suppressed duplicate refreshes while a screen load is already running.
- Restored the Activity-bound Android PrintManager when using localized/font-scaled contexts; prevented print crashes and duplicate launch.
- Added compact A5 and narrow thermal invoice layouts.
- Added credit-collection 25%/50%/Full presets and cash-effect/remaining-credit preview.
- Fixed native dropdown dimensions and Material exposed-menu behavior.
- Added native Myanmar labels/dialog translation and fixed template regex compatibility with Android's stricter regex runtime.

The dropdown implementation follows the [Material text-field documentation](https://github.com/material-components/material-components-android/blob/master/docs/components/TextField.md).
The print-context fix follows [Android PrintManager's Activity requirement](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/print/PrintManager.java).

## Test artifacts and safe reruns

Build verification: debug and unsigned release APKs build successfully.

- **17 JVM tests passed**: monetary rules, transport/CSRF/session/retry/idempotency, translation templates and invoice safety/receipt width.
- **7 isolated emulator tests passed**: navigation, sale/rotation, stock rejection, receiving confirmation, session expiry, collection presets/rotation and Android regex compatibility.
- **11 staged live phase runs passed**, including the separate final printing run. These are staged executions of one opt-in test, not 11 independent test methods.
- Android lint: **0 errors, 32 warnings** (dependency-update suggestions, Kotlin/style, localization and API-23 debug network-configuration notices).
- Final build log: `app/build/final-build.log`; isolated emulator log: `app/build/native-tests.log`; unit/lint reports: `app/build/reports/`.

Screenshots include `app/build/live-print-1.png` (one-page A5 invoice), `live-print-4.png` (receipt content with system media fallback),
`live-offline-draft.png`, `live-completed-trip.png` and `live-myanmar-profile.png`. These are emulator observations, not physical printer output.

Staged live instrumentation is in `app/src/androidTest/java/com/example/superkingsale/LiveBusinessProcessTest.kt`.
Logs: `app/build/live-*.log`. The app-private acceptance journal contains IDs/events, not credentials.
Phases are `probe`, `setup`, `native`, `transactions`, `ux`, `profile`, `draftSeed`, `draftRestore`, `settle`, `appearance`, `printing`.
`draftRestore` requires explicitly force-stopping the emulator app after `draftSeed`.
Business phases are not a general-purpose automatically repeatable reset: inspect the journal and current server state before any rerun.

Default Gradle instrumentation uses localhost MockWebServer and cannot mutate this live server.
Live execution requires BOTH building with `-PliveAcceptance=true` and passing `-e allowLiveWrites YES` to
`androidx.test.runner.AndroidJUnitRunner`, plus runtime account arguments and an explicit phase.
Use only an approved testing account and a specifically selected emulator. Never commit account arguments or credentials.
`tools/live_api.py` is an explicit CLI diagnostic helper using runtime environment credentials; it does not automatically retry writes.

## Remaining rollout checks

- Actual GPS accuracy and denied-permission recovery on field devices; no physical device was unlocked for this test.
- Physical A4/thermal printer output, service compatibility and multi-page invoices. Printing uses installed Android print services, not direct Bluetooth ESC/POS.
  The emulator's generic print service **substitutes Letter for custom 80/58/50 mm media**. App receipt content retains
  its requested physical width, but actual page/roll width requires a compatible service and verification in the system preview.
  The app provides paper-size help; opening all five choices is not proof of five matching physical paper outputs.
- Human Myanmar-language proofreading; unmatched/native or server messages can still fall back to English.
- Small/low-end devices, every supported OS version, large-volume data and sustained performance profiling. Automated device coverage is API 37, not all versions from minimum API 23.
- A real timeout after server commit and session revocation during a mutation. Deterministic retry/session tests exist, but a destructive live timing fault was not injected.
- Admin browser UI was not exercised. Office transitions were verified at the real API boundary.
- Owner-controlled release signing before distribution. The release APK is intentionally unsigned.

These are acceptance boundaries, not a claim that every device, timing edge case or translation has been exhaustively tested.
