# GPS, printer connections and Myanmar readiness

Implemented 18 September 2026. These changes do not modify backend business rules or create server records.

## GPS

- New **Profile & settings → Test device GPS** diagnostic. It displays coordinates, reported accuracy, provider and a simulated-location warning without saving anything to the server.
- Sale capture and the diagnostic share the foreground-only acquisition controller. It requests permissions in context, explains their purpose and provides app/device settings recovery.
- GPS and network fixes compete for the best fresh reading. A reading within 50 m reported accuracy finishes immediately; otherwise acquisition continues for up to 45 seconds. The 50 m target is not a new server business restriction.
- Approximate/less accurate readings require explicit confirmation. Nonfinite/out-of-range coordinates, missing accuracy, future monotonic timestamps and fixes over 30 seconds old are rejected.
- Cancellation, leaving the screen, rotation and destruction remove listeners. No background tracking or fabricated coordinates are added. Drafts remain intact when capture fails.
- API location permission handling follows [Android's runtime location guidance](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime).

### Physical observation

The owner approved testing on the connected Android 15 phone, model 25028PC03G. The device was unlocked and GPS was enabled.
The first opt-in physical test received **no fresh GPS-provider fix within 45 seconds**. It did not mock a location or create a sale.
The original coarse/fine location permissions were restored to denied after the test. Precise coordinates were not written into the test log.
At the later final APK installation, both permissions were observed granted; those current settings were left unchanged.
An outdoor/window-side retest is still needed; this result is not a physical GPS acceptance pass.

## Printing

Open **Profile & settings → Thermal printer / PDF**, or **Print invoice → Connection / PDF**.

| Connection | Implemented behavior | Boundary |
| --- | --- | --- |
| Bluetooth | Select an already-paired Classic/SPP device; runtime Nearby devices permission on Android 12+ | Not BLE, not an arbitrary vendor protocol; pair in Android settings |
| USB / OTG | Select printer/vendor-class bulk-output interface; explicit Android USB permission; partial-write handling | Phone needs USB host/OTG and compatible USB printer firmware |
| Wi-Fi / TCP | Enter host/IP and port (default 9100); connection timeout; Android 17 local-network permission | Raw TCP is unencrypted; use a trusted network |
| Android Print | Existing Android print-service/PDF workflow remains available | Install the vendor print service for non-ESC/POS printers |
| Exact-width PDF | Save nominal 50, 58 or 80 mm receipt PDFs with the system document picker | Printer/viewer scaling must be disabled and paper must match |

Paper widths: **50 / 58 / 80 mm**. Default printable widths: **360 / 384 / 576 dots**, adjustable in multiples of eight from 200–832.
Roll width and printable dot width are different; match the printer specification using the test receipt. Defaults assume common 203 dpi mechanisms.
The larger-text option and optional cutter command are explicit; cutting is off by default.

Direct output uses the common legacy **ESC/POS GS v 0 raster command**, not plain-text codepages.
Android shapes Myanmar text before sending bounded raster strips, so printer firmware does not need a Myanmar font.
The driver does not claim compatibility with every printer, BLE, ZPL, CPCL or TSPL.
Native raster/PDF receipts include textual business branding, invoice/payment/customer/items/FOC/discount/promotion/cashback/void/signature/footer details.
The existing Android HTML print path retains the remote branding logo; the direct raster/PDF path currently uses textual branding.

### Print-state safeguards

- A confirmation names the selected connection/device before any bytes are sent.
- Permission grants and rotation do not automatically start/restart a print.
- Controls disable during printing; cancellation closes the active socket/USB connection.
- Transport work and rasterization run off the main thread, with bounded bitmap memory, a 60-second overall deadline and no automatic retransmission.
- A durable in-flight marker warns after interruption/process death. Failed/cancelled jobs remain uncertain until a subsequent successful send.
- “Data sent” means bytes were written, **not** that the printer acknowledged a correct finished receipt. A partial receipt may have printed after any failure.
- PDF export uses the document picker; exported invoices are ordinary user-selected files, not the app's encrypted private storage. Share/store them appropriately.

References: [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions),
[USB host](https://developer.android.com/develop/connectivity/usb/host),
[Android 17 local-network permission](https://developer.android.com/privacy-and-security/local-network-permission),
[Epson GS v 0 reference](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lv_0.html),
[Android custom document printing](https://developer.android.com/training/printing/custom-docs).

## Myanmar copy and rendering

- Reviewed native GPS/printer/financial/error copy; standardized discount wording to **လျှော့ဈေး**.
- Added missing sign-in, confirmation, validation, stock/cash summary and interrupted-operation messages.
- Local form validation now uses the same translation layer as dialogs; previously several errors bypassed it.
- Named placeholders retain business names, IDs, quantities and amounts. Technical terms such as Bluetooth, USB, TCP, PDF, SKU and MMK intentionally remain identifiable.
- Catch-all templates containing only placeholders no longer auto-match arbitrary text. A regression test protects paper widths such as `58 mm`, amounts and business names from accidental translation/reordering.
- Receipt text uses Android Unicode shaping, not ASCII conversion or legacy printer character sets.
- `python tools/check_myanmar.py --strict` checks duplicate keys, empty translations, native placeholder parity and unmapped static UI/error calls. Its coverage is explicitly static; dynamic server/business content and every linguistic nuance are not certified by this audit.
- Rendered Myanmar test receipts are visually checked for missing glyphs/clipping. This is an assistant copy review, not independent native-speaker or business-owner proofreading sign-off.

## Hardware acceptance procedure

1. GPS: go outdoors, grant precise foreground location, capture, compare with a known position, deny/re-enable permission, turn location off/on, retry and cancel. Confirm draft preservation.
2. Select each actual printer/connection you intend to support. Print the built-in **TEST PRINT — NOT A SALE** sheet at the correct width/dot setting.
3. Check Myanmar combining marks, edge clipping, darkness, feed/cutter and readable totals. Verify a long invoice and a voided invoice.
4. Disconnect/power off the printer mid-job, cancel, rotate and relaunch. Check physical output before confirming a retry. No silent duplicate should be sent.
5. Have a Myanmar-speaking sales user review the sale, receiving, credit collection, cash return and trip-ending terminology.

Actual Bluetooth/USB/Wi-Fi printer output remains unverified until a compatible printer is connected and a person checks the paper.
Do not treat local TCP receiver tests or a rendered PDF as physical printer acceptance.

## Reproducible checks

```powershell
python tools/check_myanmar.py --strict
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug assembleRelease --max-workers=2
```

Default instrumentation is isolated from the live server. `HardwareOutputTest` covers PDFs, pagination, raster data, a local TCP receiver and native settings/language behavior.
`LocationUiTest` covers permission-denial recovery on an initially ungranted test device. `PhysicalGpsTest` is skipped unless explicitly passed `allowPhysicalGps=YES`; use only an approved unlocked phone and restore prior permission settings afterward.

### Build verification, 18 September 2026

- Debug APK, instrumentation APK and unsigned release APK built successfully (`app/build/hardware-delivery-build.log`). Release distribution still requires the owner's signing configuration.
- All **22 JVM unit tests** passed. Android lint reported **0 errors and 40 warnings**; warnings remain, including dependency/style suggestions and the deliberate durable print-job marker write.
- All **13 isolated Android instrumentation tests** passed on the API 37 emulator (`app/build/hardware-delivery-ui-tests.log`): seven sales/navigation/state tests, five printing/language tests and one GPS-permission recovery test. TCP output was checked against a localhost receiver, not physical paper.
- The final Myanmar printer-settings screenshot was inspected: paper width remains `58 mm`, and the Unicode text renders without missing-glyph boxes. Generated evidence includes `app/build/myanmar-printer-settings.png` and `app/build/receipt-test-58.pdf`.
- Static Myanmar audit passed: **2,003 dictionary entries, zero unmapped static UI/error calls**. This does not replace human proofreading.
- The physical GPS attempt is recorded separately in `app/build/physical-gps.log` and **failed to obtain a fix**; it is not included among passing software tests.
- No live business records were created or changed by these hardware/language checks.
- The final debug APK was installed on the approved phone without clearing app data. Only this project's temporary instrumentation helper package was removed from the phone; it can be reinstalled from `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` for an approved retest.
