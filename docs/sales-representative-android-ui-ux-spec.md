# Sales Representative Android UI/UX Specification

**Product:** StockFlow Inventory — Sales Representative App  
**Platform:** Native Android  
**Document status:** Development baseline  
**Version:** 1.0  
**Last updated:** 2026-09-18  
**Reference implementation:** Existing `/sales/*` React/PWA portal

## 1. Purpose

This document is the source of truth for a native Android version of the sales representative app. It defines the product structure, visual language, reusable components, interaction rules, screen behavior, and acceptance criteria required to keep Android consistent with the existing sales portal.

The Android app must preserve workflow and data parity with the existing system. Native Android conventions may replace web-specific interactions where doing so improves usability without changing business behavior.

This specification covers the representative experience only. Office and administrator screens are outside its scope.

### 1.1 Requirement language

- **Must / must not:** required for design and development acceptance.
- **Should / should not:** preferred; deviations require a documented reason.
- **May:** optional enhancement that does not change business behavior.

### 1.2 Product goals

1. Make the most frequent field tasks fast and unmistakable.
2. Prevent stock, credit, and cash mistakes before submission.
3. Keep the representative aware of connectivity, trip state, stock custody, and cash custody.
4. Behave predictably on small Android phones, tablets, slow networks, and with English or Myanmar text.
5. Reuse one token-driven component system instead of screen-specific styling.

## 2. Users and operating context

The primary user is a field sales representative who:

- works from an assigned warehouse and, when applicable, an active trip;
- receives stock issued by the office;
- sells held stock to assigned customers;
- records cash, banking, or credit sales;
- may collect customer credit;
- holds company cash until an office-confirmed handover;
- may work with intermittent connectivity, outdoors, and one-handed;
- may use English or Myanmar and increased font scaling.

The app is not an offline point-of-sale system in version 1. Previously loaded read-only information may remain visible offline, but inventory- or money-changing requests must never be queued for later submission.

## 3. Experience principles

### 3.1 Custody is always visible

Stock held, incoming stock, cash held, pending handovers, and outstanding credit must be labeled explicitly. Do not rely on color or assumed accounting knowledge.

### 3.2 One obvious primary action

Each screen should have one visually dominant action. Examples are **Create new sale**, **Approve receipt**, **Post sale**, and **Submit for confirmation**. Secondary actions must not compete with it.

### 3.3 Confirm irreversible transitions

Saving a draft is reversible. Posting a sale, approving all received stock, beginning trip ending, and submitting cash for office confirmation change an operational state and require clear review or confirmation.

### 3.4 Server values are authoritative

Prices, promotions, totals, availability, credit limits, stock balances, and cash balances returned by the server override local calculations. The UI may preview values but must re-render the server result after every mutation.

### 3.5 Status uses words, not color alone

Every state must include a readable label such as **In transit**, **Posted**, **Pending**, or **Offline**. Icons and colors reinforce the label but never replace it.

### 3.6 Recovery is designed, not improvised

Loading, empty, offline, validation, server-error, retry, and expired-session states are required screen states. A blank screen is never acceptable.

## 4. Information architecture

### 4.1 Primary phone navigation

Use a persistent Material 3 `NavigationBar` with exactly five destinations:

| Order | Destination | Icon meaning | Purpose |
| ---: | --- | --- | --- |
| 1 | Home | Dashboard | Route overview and shortcuts |
| 2 | Trip | Truck/route | Current trip, expense, and settlement progress |
| 3 | New sale | Plus/add sale | Four-step sale creation flow |
| 4 | Sales | Receipt/list | Draft and posted sale history |
| 5 | Cash | Cash/wallet | Cash custody and office handovers |

Rules:

- Use short labels in both selected and unselected states.
- Do not hide navigation labels.
- The current destination must be visibly selected and announced by accessibility services.
- Preserve each top-level destination's list position and filters when switching tabs when practical.
- Tapping the selected destination should return to that destination's root and may scroll it to the top.
- **My stock**, **Customers**, **Profile & security**, display settings, language, print settings, and sign out remain secondary destinations available from relevant shortcuts and the profile menu.

### 4.2 Adaptive navigation

| Window width | Navigation pattern | Content behavior |
| --- | --- | --- |
| Compact, under 600 dp | Bottom navigation | Single-column, edge-to-edge scaffold |
| Medium, 600–839 dp | Navigation rail preferred | One or two content columns |
| Expanded, 840 dp and above | Navigation rail or permanent drawer | Constrain readable content; use master-detail where useful |

On medium and expanded layouts, add **My stock** as a direct navigation destination, matching the wider web portal. Do not show a bottom bar and navigation rail simultaneously.

### 4.3 Destination inventory

| Android destination | Existing web route | Entry point |
| --- | --- | --- |
| Sign in | `/sales/login` | Logged-out launch |
| Home | `/sales/dashboard` | Home navigation |
| Current trip | `/sales/trip` | Trip navigation |
| My stock | `/sales/my-stock` | Home/trip/profile shortcut |
| Receiving detail | `/sales/receivings/{id}` | Pending stock row |
| New/edit sale | `/sales/new-sale` | New sale navigation or draft edit |
| Sales history | `/sales/sales-history` | Sales navigation |
| Sale detail | `/sales/sales-history/{id}` | Sale row |
| Cash hold | `/sales/cash-hold` | Cash navigation |
| Customers | `/sales/customers` | Profile menu or sale flow |
| New customer | `/sales/customers/new` | Customers or sale flow |
| Profile & security | `/sales/profile` | Profile menu |

## 5. Android implementation baseline

Use Jetpack Compose with Material 3 components unless the Android project has an established equivalent. The design system should expose semantic tokens through `MaterialTheme`, not hard-coded values inside screens.

Recommended architecture boundaries:

- one activity with Navigation Compose;
- screen-level `ViewModel` state exposed as immutable UI state;
- unidirectional events from composables to the `ViewModel`;
- repository/API layer as the only source of remote mutations;
- `SavedStateHandle` for restorable filters and draft navigation state;
- Android Keystore-backed secure storage for credentials or access tokens;
- WorkManager must not queue stock or money mutations.

The Android app must call the same authorization-enforced backend. Hidden buttons are a usability measure, not a security boundary.

## 6. Visual foundation

### 6.1 Brand and color tokens

The configured business primary color should replace the default primary token when supplied by the branding API. All derived containers and contrast colors must remain accessible.

| Semantic token | Light | Dark | Usage |
| --- | --- | --- | --- |
| Primary | `#066B63` | `#5BCBBF` | Primary actions, active navigation, focus |
| On primary | `#FFFFFF` | `#10201F` | Content on primary |
| Background | `#EEF4F4` | `#0F1419` | App/window background |
| Surface | `#FFFFFF` | `#1A222D` | Cards, sheets, dialogs |
| Surface variant | `#F2F6F6` | `#232D3A` | Grouped controls and subtle containers |
| On surface | `#172033` | `#E8EDF3` | Primary text |
| On surface muted | `#596579` | `#91A0B2` | Secondary text |
| Outline | `rgba(15,35,42,0.11)` | `rgba(255,255,255,0.10)` | Dividers and borders |
| Success | `#0E7047` | `#5BCE91` | Completed, available, online |
| Warning | `#865700` | `#F2BD55` | Pending, draft, attention |
| Error | `#B52F38` | `#FF858B` | Destructive action and failure |
| Information | `#1762A3` | `#7BBCF2` | In transit and informational state |

Color rules:

- Reserve semantic colors for state; do not use error red decoratively.
- Text and meaningful icons must meet WCAG 2.2 AA contrast.
- A custom brand color must be checked against both themes; generate or select a readable `onPrimary` color.
- Use subtle tonal containers for selected rows and status cards. Avoid large saturated surfaces except the primary action.
- Support Android system light, dark, and user-selected app theme.
- Dynamic color may be offered only as an explicit preference. It must not replace the default branded theme.

### 6.2 Typography

Use `Noto Sans Myanmar` for Myanmar glyph coverage and the Android/system sans-serif for Latin text. Bundle or verify the Myanmar font so rendering does not vary by device vendor.

| Role | Size | Weight | Typical use |
| --- | ---: | --- | --- |
| Display metric | 24 sp | Bold | Cash/stock KPI amount |
| Screen title | 22 sp | Bold | Top app bar/page title |
| Section title | 18 sp | Semi-bold | Card or section heading |
| Title | 16 sp | Semi-bold | Row title, dialog title |
| Body | 14 sp | Regular | Main content and fields |
| Label | 12 sp | Medium | Metadata and field labels |
| Eyebrow | 11 sp | Bold | Short uppercase English category only |

Rules:

- Respect system font scale to at least 200% without clipped text or inaccessible actions.
- Do not uppercase Myanmar text.
- Use tabular figures for quantities, references, and monetary values when the selected font supports them.
- Allow customer/product names to wrap to two lines. Do not truncate critical amounts or state labels.
- Display money as localized numbers plus `MMK`; do not rely on a currency symbol.

### 6.3 Spacing and sizing

Use a 4 dp base grid.

| Token | Value | Usage |
| --- | ---: | --- |
| `space-1` | 4 dp | Icon/text micro gap |
| `space-2` | 8 dp | Related content |
| `space-3` | 12 dp | Row/card internal spacing |
| `space-4` | 16 dp | Standard screen gutter and section gap |
| `space-5` | 20 dp | Large card padding |
| `space-6` | 24 dp | Major section separation |
| `space-8` | 32 dp | Empty-state separation |

- Compact phone horizontal gutters: 16 dp.
- Very narrow screens may use 12 dp, but never less than 12 dp.
- Minimum interactive target: 48 × 48 dp.
- Standard field and button height: at least 48 dp.
- Do not place two unrelated destructive/primary targets closer than 8 dp.

### 6.4 Shape, borders, and elevation

| Element | Corner radius | Elevation |
| --- | ---: | ---: |
| Small control/status | 6 dp | 0 |
| Field/button/card | 8 dp | 0–1 dp |
| Prominent card | 12 dp | 1–2 dp |
| Bottom sheet/dialog | 16 dp top or all corners | 3–6 dp |
| Pill badge | Full | 0 |

Use a 1 dp outline to separate standard cards. Elevation should communicate layering, not decoration. Avoid heavy shadows and excessive nested cards.

### 6.5 Icons and imagery

- Use one outlined Material-style icon family with consistent stroke/optical weight.
- Pair unfamiliar icons with text.
- Do not use emoji as functional icons.
- Product imagery is optional; the product name, SKU, unit, availability, and price remain primary.
- Content descriptions should state the action, not the icon name: **Print invoice**, not **Printer icon**.

## 7. App shell

### 7.1 Top app bar

Root destinations use a small top app bar containing:

- business logo/mark and business name on Home;
- destination title on other roots;
- compact connectivity indicator when offline or reconnecting;
- representative avatar/profile action.

Child destinations use an Up button, concise title, and only the actions relevant to that record. The Android system Back action must follow the same hierarchy as Up, except that Back may first close a sheet, dialog, keyboard, or expanded menu.

### 7.2 Safe areas and edge-to-edge

- Draw edge-to-edge, but inset app bars, scrollable content, bottom navigation, sheets, snackbars, and bottom actions for system bars and display cutouts.
- Bottom content must never be obscured by gesture navigation or the software keyboard.
- Sticky bottom actions must move above the IME while a field is focused.

### 7.3 Profile menu

Use a modal bottom sheet on compact screens and a dropdown menu on larger screens. It contains:

1. representative name, username, and role;
2. Customers;
3. Profile & security;
4. theme preference;
5. comfortable/compact display preference if retained;
6. English/Myanmar language selection;
7. print settings;
8. Sign out, visually separated at the end.

Sign out must clear sensitive local session data and return to Sign in.

## 8. Reusable component specification

### 8.1 Buttons

| Type | Use | Rules |
| --- | --- | --- |
| Filled primary | One main action | Maximum one per action region |
| Tonal/outlined | Secondary action | May sit beside primary |
| Text | Low-emphasis navigation/action | Avoid for irreversible actions |
| Error filled/outlined | Destructive transition | Confirm when impact is material |
| Icon button | Familiar, compact action | 48 dp target and content description |

All buttons must support default, pressed, focused, disabled, loading, and error-recovery states. During submission, keep the button width stable, show progress, and block repeat taps.

### 8.2 KPI card

Structure:

1. short label;
2. large numeric value;
3. contextual caption;
4. optional semantic icon.

Use a two-column grid when each card remains at least 156 dp wide; otherwise use one column. The leading KPI may use a primary-tinted surface.

### 8.3 Operational list row

Use a consistent row anatomy:

- leading state/product/customer icon;
- primary label;
- one or two metadata lines;
- trailing amount, quantity, or status;
- chevron only when the full row navigates.

The entire row should be tappable when it has one navigation outcome. Minimum height is 64 dp; use 72–88 dp for multi-line content.

### 8.4 Status badge

Use a text label, optional 6 dp dot, semantic text color, and a pale tonal background. Required mappings:

| State family | Tone |
| --- | --- |
| Active, received, posted, confirmed, online, available | Success |
| Draft, pending, ending, attention | Warning |
| In transit, informational | Information |
| Failed, reversed, voided, blocked | Error when action is needed; otherwise neutral |
| Inactive, cancelled, historical | Neutral |

### 8.5 Text fields and selectors

- Labels remain visible after input; placeholders are examples, not labels.
- Required fields are identified in text or semantics.
- Show inline validation directly under the affected field.
- Use numeric keyboards for integer quantities and decimal/amount keyboards for money.
- Never accept formatted display separators as the stored numeric value.
- Customer and product selection use searchable full-screen sheets on compact devices.
- Keep user input when a recoverable server error occurs.

### 8.6 Tabs and filters

- Use primary tabs for peer views such as **Current stock / Issue history** and **Current trip / All returns**.
- Sales filters open in a modal bottom sheet on phones.
- A filter sheet must have persistent **Clear** and **Apply filters** actions.
- Applied filters must be summarized with removable chips or an active-filter count.
- Date range controls must enforce From ≤ To.
- Trip options update from the selected duration; unavailable choices are disabled with explanatory text.

### 8.7 Dialogs and bottom sheets

Use an alert dialog for short confirmation and a modal bottom sheet/full-screen destination for forms or searchable lists.

Confirmation copy must name the object and consequence. Example: **Approve 48 base units from Yangon Warehouse? All items will be received together.**

Dialog action order follows Android convention: dismissive action first, confirming action last. Do not dismiss by tapping outside while a transaction is submitting.

### 8.8 Feedback components

- **Snackbar:** short success or non-blocking notice; keep visible 4–6 seconds when it contains a reference.
- **Inline banner:** offline state, trip state, or recoverable screen-level failure.
- **Field error:** validation tied to one input.
- **Alert dialog:** irreversible confirmation, session expiry, or exceptional blocking condition.
- **Progress indicator:** indeterminate for unknown waits; determinate only when actual progress exists.

Avoid using toast messages for transaction results because they are easy to miss and weak for accessibility.

### 8.9 Loading, empty, and error states

Every remote screen must implement:

1. initial loading;
2. content;
3. empty with a specific explanation and useful action when available;
4. blocking error with Retry;
5. pull-to-refresh where appropriate;
6. stale/read-only offline content with its last-updated context when known.

Use skeletons only when they closely match the final layout. Never show fabricated monetary or stock values while loading.

## 9. Screen specifications

### 9.1 Sign in

**Purpose:** authenticate a representative into the sales portal.

Required content:

- business brand;
- username and password;
- show/hide password control;
- primary **Sign in** button;
- clear invalid-credentials, inactive-account, no-network, and server-unavailable states.

Behavior:

- Use autofill-compatible fields.
- Keep username after an authentication failure; clear the password only when appropriate.
- Restore a valid session at launch without flashing the sign-in screen.
- An authenticated non-representative must see a clear forbidden/access-state screen, not representative data.

### 9.2 Home — Route overview

Required hierarchy:

1. date/as-of context and **Route overview** title;
2. representative status/code;
3. KPI cards: **Cash hold**, **Today's sales**, **Current stock**;
4. full-width primary **Create new sale** action;
5. recent sales, with a link to full history;
6. current stock preview, with **View all**;
7. pending stock, with count and **Open receiving**;
8. sales-report/history shortcut.

Show no more than a useful preview of recent items. Long lists belong to their dedicated destinations.

### 9.3 Current trip

Required states: loading, no active trip, planning, operation, ending, and completed/historical when returned by the API.

When a trip exists, show:

- reference, title, region, warehouse, vehicle, and status;
- metrics: stock held, paid sales, cash held, latest customer credit;
- payment-method breakdown for sales and credit collections;
- actions: New sale, View stock, Return cash, Record expense;
- recent sales and trip expenses;
- trip-ending guidance.

**Record expense** fields are Description, Amount, and optional Notes. After save, explicitly state that the expense was recorded but did not reduce cash held.

**Begin trip ending** is destructive to the selling workflow. Its confirmation must state that new sales will be blocked and stock/cash return will begin. Once status is `ending`, remove or disable New sale and show a persistent warning banner.

### 9.4 My stock

Top-level content:

- **On hand** and **Incoming** KPI cards;
- pending-count status;
- tabs: **Current stock** and **Issue history**.

Current stock contains:

- Pending stock list with transfer reference, warehouse, product count, base-unit total, and state;
- searchable available-products list;
- paid and FOC balances shown separately;
- selling-unit equivalents where supplied;
- read-only wording.

Issue history contains completed stock issues. Representatives must not receive edit affordances for stock balances or transfer lines.

### 9.5 Receiving detail

Show transfer reference, dispatch time, source warehouse, status, and all product lines. Each line displays product, selling-unit equivalent where relevant, paid base units, FOC base units, and total base units. Show a total row.

For an in-transit transfer, display **Approve receipt** as the main action. Confirmation must:

- name the reference, source warehouse, and total quantity;
- state that all products are approved together;
- state that partial receipt is unavailable;
- provide a safe way to go back and report a discrepancy.

After approval, reload from the server and show **Received**. Disable repeat submission while pending. A representative must never be able to approve another representative's transfer.

### 9.6 New or edit sale

Use a four-step wizard:

1. **Information**
2. **Products**
3. **Quantity**
4. **Review & submit**

On phones, show the current step and overall progress without forcing all step labels into a narrow row. Completed steps may be revisited; future steps remain unavailable until current validation succeeds. Preserve entered data when moving backward.

#### Step 1 — Information

Required fields and states:

- searchable Customer;
- **New customer** shortcut;
- Payment type: **Paid now** or **Credit**;
- for Paid now, server-configured payment methods such as Cash or Banking;
- customer credit card showing available, outstanding, and limit, or a clear cash-only state;
- optional Notes;
- required sale creation location for a new sale.

Location behavior:

- Request precise location in context after the user starts a new sale, not at first app launch.
- Explain before the permission prompt: location is attached to the sale record for office verification.
- Show states: required, locating, captured with approximate accuracy, permission denied, device location disabled, and retry.
- Request only foreground location. Background location is out of scope.
- An edited draft retains its original sale location unless the backend explicitly defines otherwise.
- Do not advance or create a new sale without a valid captured location.

Credit is selectable only when the chosen customer is credit-enabled. Insufficient credit must be detected before posting and also handled if rejected by the server.

#### Step 2 — Products

Show only products available to the representative. Each selectable row displays:

- product name, SKU, and base/default unit;
- paid availability;
- FOC availability;
- default selling-unit price for the selected region;
- selected state.

At least one product is required. Product search should be provided when the list is longer than 10 items. Selection must not silently reset quantities already entered for retained products.

#### Step 3 — Quantity

For every selected product provide:

- paid selling unit;
- whole-number paid quantity, minimum 1;
- FOC unit;
- whole-number FOC quantity, minimum 0;
- paid/FOC stock available in base units;
- line price preview and any server-defined discount/promotion result.

Convert selling units to base units for stock validation. Paid quantity must not exceed paid stock; FOC quantity must not exceed FOC stock. Do not combine paid and FOC balances in the input controls.

#### Step 4 — Review & submit

Show a scannable review of:

- customer;
- payment type and payment method;
- stored location state;
- notes;
- every product, paid/FOC quantities and units;
- unit price, discounts, promotions, cashback, line totals;
- paid and FOC base-unit summary;
- final sale total.

Actions:

- **Back** edits earlier steps;
- **Save draft** saves a reversible record;
- **Post sale** performs the final transaction.

Posting rules:

- State that posted sales cannot be edited.
- Disable both transaction actions while either is in progress.
- Send an idempotency key and handle repeated responses safely.
- On success, navigate to Sale detail and show the server reference.
- On timeout or uncertain outcome, do not invite an immediate blind retry. Tell the user to check Sales history for the reference, then refresh.

### 9.7 Sales history

Summary cards show posted-only Gross sales, Cash sales, Credit sales, and Units sold.

Each row shows customer, reference, timestamp, item/unit summary, payment type, status, and total. Posted rows open Sale detail. Draft rows provide View, Edit, Post, and Delete permanently; destructive deletion requires confirmation.

Filters:

- duration: Today or Date range;
- From and To when using a range;
- Trip, populated from the duration;
- Customer;
- Product;
- Payment: Cash & credit, Cash, or Credit;
- status where supported by the API.

Keep filter state while viewing a detail and returning. Pagination may use explicit pages or cursor-based incremental loading, but must preserve order and result count semantics.

### 9.8 Sale detail

Header content:

- sale reference;
- customer and timestamp;
- status badge;
- Print invoice when available;
- Edit draft only when status is draft.

Sections:

- Sale information: customer, payment type/method, warehouse, region, created/posted/voided dates, notes, void reason;
- Line items: product, unit, paid quantity, FOC, unit price, discount, line total, promotion details;
- totals: product count, paid and FOC totals, discounts, cashback, final total.

Posted records are immutable in the representative app.

### 9.9 Cash hold

Required context:

- current trip and status, or a no-active-trip notice;
- Current hold;
- Available to return after pending handovers;
- Sales added to hold;
- Confirmed returned;
- opening cash, cash credit collected, pending handover, and total trip responsibility;
- office handover history;
- append-only custody activity.

**Return cash** is available only for an eligible active trip and a positive available amount. The form contains Amount and optional Notes. The confirmation/action copy must explain:

- the handover is linked to the current trip;
- it awaits office confirmation;
- pending submission reserves the available-to-return amount;
- cash hold itself decreases only after office confirmation.

A pending handover may be cancelled with a mandatory reason. Cancellation releases the reservation but does not change cash hold. Confirmed transactions are immutable.

### 9.10 Customers

Customer list supports search by code, name, phone, or township. A row shows name, code, type, warehouse/region coverage, phone/location, active state, and outstanding credit.

If outstanding credit is positive, provide **Collect credit**. Its sheet displays customer, current outstanding, Amount, Payment method, and optional Notes. Amount must be greater than zero and not exceed outstanding credit. Clearly indicate whether the selected method adds to representative cash hold or is paid directly.

New customer fields:

- Customer name;
- Customer type;
- Phone;
- Region;
- Township;
- Address;
- Notes.

Newly created representative customers start cash-only with a zero credit limit unless backend rules change. Say this before creation.

### 9.11 Profile & security

Provide:

- display: theme and supported font/display scale;
- personal details supported by the API;
- English/Myanmar language;
- print settings;
- password change;
- sign out.

Password forms must not log values, expose them in screenshots of app analytics, or retain them after leaving the screen.

## 10. Workflow and state rules

### 10.1 Sale state

```text
Local entry → Draft → Posted
                 ↘ Deleted
Posted → Voided (office-controlled where authorized)
```

- Draft is editable and may be deleted.
- Posted is immutable to the representative.
- Voided remains visible as history.

### 10.2 Stock issue state

```text
Dispatched / In transit → Received
                        ↘ Reversed (office-controlled)
```

- Receiving is all-or-nothing in version 1.
- Stock becomes available only after server-confirmed receipt.

### 10.3 Cash handover state

```text
Pending → Confirmed
        ↘ Cancelled
        ↘ Reversed (office-controlled, if supported)
```

- Pending reduces **available to return**, not **current hold**.
- Confirmed reduces cash custody.
- Historical ledger entries are append-only.

### 10.4 Trip state

```text
Planning → Operation → Ending → Completed
```

- Selling is available only when backend state permits it.
- Beginning Ending blocks new sales.
- Office completion depends on required stock/cash settlement.

## 11. Connectivity and offline behavior

### 11.1 Global status

- Show an offline banner below the top app bar whenever validated connectivity is unavailable.
- The banner text should be: **You're offline. Previously loaded information is read-only. Connect to the internet to complete transactions.**
- When the network returns, show a short **Back online** snackbar and refresh stale summaries where safe.
- Do not treat network transport as proof that the API is reachable; distinguish offline from server unavailable when possible.

### 11.2 Mutations

The following must require a live API connection and must never be added to a background retry queue:

- receive stock;
- create, update, delete, or post a sale;
- create a customer;
- collect credit;
- submit or cancel a cash handover;
- record an expense;
- begin trip ending;
- change password or other security-sensitive account data.

Disabled offline actions should explain why when tapped or focused. The standard message is: **Internet connection is required to complete this transaction.**

### 11.3 Cached content

- Cached content must be visibly read-only and should show **Last updated** when known.
- Never merge cached stock or cash values into a later transaction without refreshing them from the server.
- Sensitive cache must be minimized and encrypted where persisted.
- Clear representative-specific cache on sign out or account change.

## 12. Validation and failure handling

### 12.1 Validation order

1. Validate obvious format and required fields locally.
2. Submit only when the local form is valid and online.
3. Treat server validation as authoritative.
4. Map server field errors to their inputs and show one screen-level summary when helpful.
5. Move focus and accessibility focus to the first invalid field.

### 12.2 HTTP/application outcomes

| Outcome | UI response |
| --- | --- |
| 400/422 validation | Inline errors; retain input |
| 401 expired session | Blocking sign-in-required state; protect unsaved non-sensitive form state only if safe |
| 403 forbidden/inactive | Access-state screen; no leaked record details |
| 404 record absent | Not-found state with Up/back path |
| 409 conflict/stale stock or credit | Explain changed balance; refresh authoritative data; require a fresh review |
| 429 rate limited | Preserve input and show retry guidance |
| 5xx | Screen/form error with Retry when safe |
| Timeout after mutation request | Mark outcome uncertain; check server/history before retry |

Never convert a business rejection into a generic **Something went wrong** message when the backend supplies actionable detail.

## 13. Permissions and privacy

### 13.1 Location

- Ask only when starting a new sale and explain the reason first.
- Request foreground precise location.
- If denied once, keep **Allow location** available.
- If permanently denied, provide **Open settings** and explain the path.
- Do not request background location.
- Display approximate accuracy; do not display raw latitude/longitude in the standard sale UI.

### 13.2 Notifications

Notification permission is optional unless a separate approved notification feature is introduced. Core receiving and transaction workflows must not depend on it.

### 13.3 Sensitive data

- Use TLS for all API traffic.
- Never write tokens, passwords, full request bodies, or customer-sensitive fields to production logs.
- Redact sensitive data from crash and analytics payloads.
- Prevent another signed-in representative from seeing prior-user cached content.
- Consider `FLAG_SECURE` only for screens whose business risk justifies blocking screenshots; do not apply it indiscriminately without product approval.

## 14. Localization and content design

### 14.1 Language support

- English and Myanmar are required.
- All visible strings, accessibility labels, plural forms, validation messages, and notification text must use Android string resources.
- Do not concatenate translated fragments to form sentences.
- Layouts must tolerate at least 30% text expansion.
- Use Unicode text end-to-end and test Myanmar line breaking on low- and mid-range target devices.

### 14.2 Voice and terminology

Use plain, operational language and the same nouns throughout:

- **Cash hold**: company cash currently in representative custody.
- **Available to return**: cash hold less pending submissions.
- **Cash handover / Return cash**: representative submission to the office.
- **Pending stock**: dispatched stock awaiting representative confirmation.
- **FOC**: free-of-charge inventory, always shown separately from paid inventory.
- **Post sale**: final, immutable sale action.
- **Save draft**: reversible sale record.

Use sentence case. Avoid technical API terms, database names, and unexplained abbreviations other than established business language such as FOC and MMK.

### 14.3 Dates and numbers

- Format dates and times with the selected locale and device time zone, while preserving server timestamps internally.
- Use localized grouping separators for display.
- Quantities should not show unnecessary decimals.
- Monetary inputs and displays must avoid floating-point arithmetic; use the backend's integer/decimal contract.
- References and SKUs must remain unchanged and selectable/copyable where useful.

## 15. Accessibility

The acceptance target is WCAG 2.2 AA as applicable to native mobile and Android accessibility guidance.

Required behavior:

- 48 × 48 dp minimum touch targets;
- text contrast at least 4.5:1, large text and meaningful non-text contrast at least 3:1;
- TalkBack labels, roles, values, states, and action hints;
- logical traversal matching visual order;
- headings exposed as accessibility headings;
- live announcements for validation results, successful transactions, and connection changes;
- selected, expanded, disabled, and error states exposed semantically;
- no information encoded by color alone;
- support switch access, keyboard navigation on tablets, and external keyboards;
- respect Remove animations/reduced motion settings;
- no auto-advancing content;
- no gesture-only action without a visible alternative.

For a currency KPI, TalkBack should read the label and value together, for example: **Current cash hold, 125,000 MMK**.

## 16. Motion and haptics

- Use standard Material motion, generally 100–300 ms.
- Use crossfade/content-size animation only when it clarifies state change.
- Do not animate monetary totals in a way that delays reading the final value.
- Respect system animator-duration and reduced-motion settings.
- A subtle haptic may acknowledge a successful final transaction or destructive confirmation; never use repeated haptics for routine taps.

## 17. Compose component contract

Create reusable app components or wrappers with stable names similar to:

- `StockFlowScaffold`
- `StockFlowTopAppBar`
- `RepresentativeNavigationBar`
- `ConnectionBanner`
- `KpiCard`
- `OperationalCard`
- `OperationalListItem`
- `StatusBadge`
- `PrimaryActionButton`
- `AsyncButton`
- `FormFieldError`
- `ScreenErrorState`
- `EmptyState`
- `LoadingState`
- `FilterBottomSheet`
- `ConfirmationDialog`
- `MoneyText`
- `QuantityText`

Component APIs should accept semantic values such as status tone and button role instead of arbitrary colors. Previews must cover light/dark theme, English/Myanmar, normal/large font, enabled/disabled/loading/error, and compact/expanded widths.

## 18. UI state and event contract

Each screen's UI state should explicitly model:

```kotlin
data class ScreenUiState<T>(
    val isInitialLoading: Boolean = true,
    val data: T? = null,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isOffline: Boolean = false,
    val isStale: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: UiMessage? = null,
    val blockingError: UiError? = null,
)
```

This example is a behavioral contract, not a required exact class. Do not represent loading by erasing already visible data during refresh. One-shot navigation and snackbar events must not replay after rotation or process recreation.

## 19. Analytics and diagnostics

If analytics are approved, record workflow events without customer names, phone numbers, addresses, notes, credentials, coordinates, or monetary values. Useful events include:

- screen viewed;
- sale step completed or abandoned;
- location permission outcome;
- transaction success/failure category;
- receiving confirmation;
- filter applied;
- retry used;
- offline mutation blocked.

Correlate support issues with a safe request ID and server reference. Display the request ID in expandable error details only when available.

## 20. Design and development acceptance checklist

### 20.1 Global

- [ ] Five primary phone destinations appear in the specified order.
- [ ] Light and dark themes use semantic tokens with accessible contrast.
- [ ] Business branding can replace the default primary color safely.
- [ ] All tap targets are at least 48 dp.
- [ ] System bars, cutouts, gesture navigation, and IME insets are handled.
- [ ] English and Myanmar layouts pass with 200% font scaling.
- [ ] TalkBack traversal, labels, headings, and state announcements pass.
- [ ] Rotation and process recreation do not repeat completed mutations.

### 20.2 State completeness

- [ ] Every remote screen has loading, content, empty, error, retry, refreshing, and offline/read-only behavior.
- [ ] Buttons have default, pressed, disabled, and submitting states.
- [ ] Field errors appear beside fields and remain understandable without color.
- [ ] Session expiry and inactive-account behavior are explicit.

### 20.3 Transaction safety

- [ ] All stock/money mutations are blocked offline and never queued.
- [ ] Repeat taps cannot create duplicate transactions.
- [ ] Idempotency keys are used where supported.
- [ ] Timeout/uncertain outcomes instruct the user to verify history.
- [ ] Server-returned values replace previews after mutation.
- [ ] Conflict responses refresh stock, credit, or cash and require review.

### 20.4 Core flows

- [ ] A representative can receive all items in an assigned transfer and cannot partially receive it.
- [ ] A representative cannot see or receive another representative's transfer.
- [ ] A new sale requires customer, products, valid quantities, and captured foreground location.
- [ ] Paid and FOC stock remain visually and logically separate.
- [ ] Credit is disabled for cash-only customers and constrained by current available credit.
- [ ] Draft sales are editable; posted sales are immutable.
- [ ] Sale success shows the server reference and detail.
- [ ] Pending cash handover changes available-to-return but not current hold.
- [ ] Confirmed cash handover updates custody after server confirmation.
- [ ] Beginning trip ending removes the ability to create new sales.

### 20.5 Device test matrix

At minimum, test:

- 360 × 640 dp compact phone;
- 412 × 915 dp modern phone;
- 600 dp-width small tablet/foldable;
- 840 dp-width tablet;
- Android gesture and three-button navigation;
- portrait and landscape where not intentionally locked;
- light and dark theme;
- English and Myanmar;
- 100%, 130%, and 200% font scale;
- TalkBack and Switch Access;
- offline, slow, timeout, server error, and session expiry;
- denied, permanently denied, disabled, and successful location permission paths.

## 21. Definition of done for a screen

A screen is complete only when:

1. it uses shared tokens and components;
2. all specified states are implemented;
3. navigation and Android Back behavior are correct;
4. server authorization and values remain authoritative;
5. offline transaction safety is enforced;
6. English/Myanmar and large-font layouts are verified;
7. TalkBack semantics and traversal are verified;
8. phone and tablet screenshots have been compared with the approved design;
9. unit/UI tests cover its critical state transitions;
10. no critical or high-severity accessibility issue remains.

## 22. Reference hierarchy

When implementations disagree, use this order:

1. backend authorization, validation, and transaction rules;
2. this Android UI/UX specification;
3. current representative workflow in the React/PWA portal;
4. platform-default Material 3 behavior;
5. individual mockups or developer interpretation.

Any intentional difference from the existing representative workflow must be documented as a product decision and reviewed before release.
