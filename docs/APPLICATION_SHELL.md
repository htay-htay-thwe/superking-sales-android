# Application shell contract

The shell owns shared chrome; feature screens keep their existing content and state behavior.

- **Insets/system bars:** `MainActivity` disables decor fitting and applies status-bar, cutout, and IME insets to `@id/shell`. The bottom navigation is hidden while the keyboard is visible.
- **Splash transition:** the AndroidX splash screen uses the branded adaptive icon and fades out over 180ms after the first activity frame.
- **Phone header:** a 64dp header contains back navigation, brand identity, workspace label, connection status, and a 48dp account action.
- **Large-screen header:** the same identity/account row remains at the top; navigation moves into a horizontally scrollable top rail for `w840dp` layouts.
- **Phone navigation:** `BottomNavigationView` remains pinned below the content and above the gesture/navigation inset.
- **Account area:** initials are always available; the representative name is shown on wide layouts and actions remain in the account menu.
- **Offline banner:** the connection message is directly below the shared header/navigation divider and is announced through the existing live status behavior.
- **Global padding:** shell chrome uses the foundation spacing tokens; feature layouts continue to use the responsive gutter resources.
- **Content width:** `WorkspaceColumn` centers and caps the application surface at the foundation 1120dp maximum, with 16/24/80dp responsive outer gutters.

Authoritative implementation: `MainActivity.kt`, `activity_main.xml`, `WorkspaceDesign.kt`, and `values/foundation.xml`.
