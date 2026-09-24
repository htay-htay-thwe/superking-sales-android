# Pull-to-refresh acceptance — 18 September 2026

The shared native screen layout now uses AndroidX SwipeRefreshLayout 1.2.0. Persistent Refresh buttons are removed; Search/Filters and error Retry actions remain.

## Behavior

- Home, Trip, Sales, Cash, Stock, Customers, Receiving and Sale detail reload their current server data, retaining filters, tabs and page selection.
- Customer/Profile forms retain unsaved field values. Sale steps reload availability without clearing the local draft or changing its step.
- Sign-in refresh retries session restoration, never submits typed credentials.
- Pulling only refreshes at the top of the visible list/form. A TalkBack Refresh action provides a non-gesture alternative.
- Refresh is blocked during business mutations; loading disables submission controls. Repeated gestures do not launch overlapping refreshes.
- Spinner state follows ViewModel loading, including recreation. Errors stop it and preserve existing content.
- GPS acquisition, print jobs and modal transaction submissions remain explicit actions.

## Verification

- Debug app/test builds succeeded; 22 JVM tests passed. Lint: zero errors, 40 warnings (`app/build/swipe-final-build.log`).
- Four new emulator tests passed: eight data destinations, customer/profile input retention, sale draft retention across refresh/recreation, and failed-refresh recovery with top-of-list detection. These tests use isolated API fixtures and assert no business mutations.
- The initial 17-test regression run passed 16 tests. The only failure was an older session-expiry test still clicking the intentionally removed Refresh button (`app/build/swipe-ui-tests.log`).
- That test now performs the real swipe gesture; its targeted rerun passed (`app/build/swipe-session-retest.log`). No application change was required for that correction.
- Myanmar static audit still passes: 2,003 dictionary entries, no unmapped static UI/error calls.
- Updated debug APK installed on the approved phone without clearing app data. Physical-phone swipe behavior was not separately acceptance-tested.

Reference: [AndroidX release documentation](https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout).
