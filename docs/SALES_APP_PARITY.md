# Sales representative parity and handoff

Reference: `D:\xampp\htdocs\inventory`, read-only. No Laravel source was changed.
Authorized live QA records were created on 2026-09-18; see [live acceptance report](LIVE_ACCEPTANCE_REPORT.md).
Scope is the **sales representative portal**, not office/admin functionality.

## Navigation and features

| Reference flow | Native destination and implemented behavior |
| --- | --- |
| Login / existing session | Representative-only Sanctum session, CSRF bootstrap, encrypted cookie persistence, expiry returns to sign-in |
| `/sales/dashboard` | Home: today's sales, stock/cash KPIs, recent sales, pending receiving, quick links |
| `/sales/trip` | Current trip, region/warehouse/vehicle, stock/financial summaries, expense entry, begin-ending confirmation |
| `/sales/new-sale` | Four steps: information, products, quantity, review/submit; native searchable pickers and inline customer creation |
| Sale information | Active customer, trip region prices, cash/banking or credit, payment method, notes, fresh creation GPS |
| Products / quantity | Paid and FOC units/quantities, conversion factors, separate stock checks, percentage discount, line promotion/title |
| Review / submit | Cashback, credit/total checks, save draft, server-total confirmation before post; posted sales are immutable |
| `/sales/sales-history` | Paged list, summary, search/status/payment filters, duration-dependent trip options and native date pickers |
| Sale detail | Items, totals, audit information, creation map, draft edit/post/delete confirmations, print |
| Representative stock | Stock balance search/paging, pending receiving, receiving history |
| Receiving detail | Dispatched items/FOC/audit and explicit receive-all confirmation; no partial receiving |
| Customers | Assigned-region list/search, customer/credit details, create cash-only customer, outstanding-credit collection |
| `/sales/cash-hold` | Cash hold/available/pending, current-trip or all returns, cancellation reason, cash transaction ledger |
| Profile | Account/representative details, password change, sign-out |
| Appearance | Light/dark/system, density, font scale, English/Myanmar, per-user invoice paper preference |
| Invoice print | Android print/PDF dialog, A4/A5/80/58/50 mm, branded contact/footer/logo, paid/FOC/discount/promotion/cashback details, void marking |
| Printer connections | Bluetooth Classic/SPP, USB/OTG bulk output and Wi-Fi/TCP ESC/POS raster; selectable 50/58/80 mm, printable dots, test receipt and exact-width PDF |
| GPS diagnostic | Foreground-only test without server writes, reported accuracy/provider, permission/settings recovery, cancellation and approximate-fix confirmation |

Home, Trip, New sale, Sales, and Cash keep the source bottom-navigation order. Toolbar overflow holds Stock,
Customers, Profile/settings, and Sign out. Android Back returns from secondary screens.
Native confirmations, pickers, permission handling, and print services replace browser interactions.

## API contract

Base URL: `https://www.superkingmyanmar.com/public/`.
Authentication uses `sanctum/csrf-cookie`, `api/auth/login` (`portal: sales`), `api/sales/me`, and `api/auth/logout`.
The client sends the same origin, referer, XMLHttpRequest, JSON accept, cookie and X-XSRF-TOKEN headers expected by the source.

Sales endpoints are under `api/sales/`:

- `dashboard`, `current-trip`, `stock`, `receivings`, `receiving-history`, `receivings/{id}`, `receivings/{id}/receive`.
- `sale-options`, `sale-history-options`, `sales`, `sales/{id}`, `sales/{id}/post`.
- `customer-options`, `customers`, `credit-collections`.
- `cash-hold`, `cash-submissions`, `cash-submissions/{id}/cancel`, `cash-transactions`.
- `trips/{id}/expenses`, `trips/{id}/begin-ending`, `profile`, `profile/password`.

The backend remains authoritative for all permissions, prices, totals, stock, credit, and trip-state transitions.
HTTP validation fields, authorization failures, network errors, and request IDs are surfaced instead of fabricating success.

## Business rules preserved

- New sales and credit collections require an operating trip; trip-ending is explicit. Cash return remains available during operation/ending.
- Creation GPS is collected through Android location APIs. New saves require a fix captured within two minutes;
  edits do not send location fields. The shipping app never fabricates coordinates or bypasses permissions.
  The labelled emulator acceptance sale used explicitly simulated GPS, not a field location.
- Paid and FOC stock are validated independently in base units. Regional selling prices and remaining credit are used.
- Quantities and MMK values are whole numbers; discounts accept at most two decimals. Decimal arithmetic uses half-up rounding.
- Draft posting is a separate request after reviewing the server total; existing invoice-level promotion values are preserved on edit.
- Cash expenses are recorded without reducing cash held. A pending cash return reserves available cash;
  only office confirmation reduces the actual cash balance.
- New customer creation does not grant credit; office-controlled credit eligibility is retained.

## State, reliability, and performance

- Lifecycle-aware StateFlow collection and ViewModels keep network operations out of Fragments' view lifetimes.
- ViewModels retain loaded data during retry; filters, pagination, new-customer fields and financial dialog inputs use SavedStateHandle.
- Sale drafts have per-user/per-edit slots in encrypted storage. Exact uncertain create bodies are persisted before submission.
- Durable idempotency keys protect endpoints supported by the Laravel middleware. The client disables automatic transport retries;
  unsupported mutations such as expense/customer creation are not silently replayed.
- Repository revisions refresh affected screens after successful mutations; lists use RecyclerView/ListAdapter/DiffUtil and paged requests.
- Sale options are loaded once per screen and refreshed after related mutations; product/customer picking searches the loaded options locally.
- Busy controls prevent repeated taps, validation retains input, and network state is visible. No offline write queue is claimed.
- Server cookies/local drafts are encrypted, backups are disabled, redirects to other hosts are blocked, and no credentials are logged.

## Validation and remaining acceptance work

Default automated checks are isolated from the live server. Separately opted-in live tests used the approved testing account:

- JVM tests: money rounding/discounts/quantity validation; JSON resource parsing; CSRF and cookie restore;
  durable idempotency across repository recreation; session clearing; no automatic mutation retry; invoice escaping/branding/translation.
- Emulator tests: primary/secondary navigation; four-step sale draft, quantity retention across Activity recreation and single-save submission;
  insufficient-stock rejection; receive-all confirmation without posting; expired-session sign-in and private-data clearing.
- Build/test results and live phase logs are recorded in [the acceptance report](LIVE_ACCEPTANCE_REPORT.md).
  Generated reports are in `app/build/reports/`; the default emulator result is `app/build/native-tests.log`.

Live read-only checks: `/api/health` returned healthy database/cache, CSRF returned 204 with session cookies,
and unauthenticated `/api/sales/me` returned 401. Authenticated business flows subsequently passed;
trip TRP-000009 was completed with zero stock/cash variances and no remaining customer debt.

Live/native checks completed with controlled records:

1. Login/session restoration, region/stock/trip assignments and server response compatibility on the deployed revision.
2. Emulator GPS and a full create-draft → review → post sale; stock/cash/credit reconciliation.
3. Draft editing/deletion, cash and banking credit collections, customer creation and history filters.
4. Receiving, expense entry, pending cash return/cancel/office confirmation and trip ending.
5. Offline-save blocking, force-stop draft/session restoration, rotation and live create idempotency replay.

Still requiring rollout acceptance: physical GPS permission/recovery and accuracy, real printers, low-end/small-screen
devices, human Myanmar-language review, and an actual connection loss after a server commit or session revocation during a mutation.

The source Myanmar dictionary is supplemented with reviewed native GPS, printing, validation and financial messages.
Dynamic/server messages can still fall back to English; human proofreading remains a separate sign-off.
Direct ESC/POS raster now supports Bluetooth Classic/SPP, USB/OTG and TCP, alongside Android print services.
Physical printer compatibility and output must still be verified. See [hardware implementation and acceptance](HARDWARE_AND_MYANMAR.md).
Views and appearance are intentionally native rather than pixel-identical to the website.
Minimum-SDK behavior is statically checked; the instrumentation run uses an API 37 emulator, not every supported Android version.
Release distribution still requires the owner's signing configuration. The remaining device/field acceptance must be signed off before rollout.
