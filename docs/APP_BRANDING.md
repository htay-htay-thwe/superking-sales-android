# Superking application branding

## Delivered assets and integration

- Original user-supplied logo is copied byte-for-byte to `app/src/main/res/drawable-nodpi/superking_logo.jpg`. The source in Downloads is unchanged.
- Adaptive launcher and round icons: `mipmap-anydpi-v26`, with 20% foreground insets, a white background and the original full-colour mark.
- Android 13+ themed icon: `mipmap-anydpi-v33`, using the generated transparent eagle alpha mask. Launcher theme support must be enabled by the user.
- Older Android fallback icons: `mipmap-anydpi/ic_launcher.xml` and `mipmap-anydpi/ic_launcher_round.xml`, with a retained high-resolution source and runtime filtered scaling.
- AndroidX Core SplashScreen 1.2.0 handles startup on supported Android versions. Light/dark background resources, a white logo badge and `postSplashScreenTheme` hand off to the existing app theme. No separate splash Activity, artificial delay, animation loop or network-dependent splash hold.
- Sign-in uses the same full-colour brand icon; the logo is decorative for accessibility because the adjacent heading already names the business.
- `drawable/ic_notification_brand.xml` is a white alpha-only small-icon resource ready for future notifications. No notification feature, background tracking or permission prompt was added.
- This is the application identity/startup package, not a redesign of business screens, Play Store listing publication, signing setup or production acceptance.

## Generated asset provenance

The imagegen skill's built-in image-generation tool produced the supplementary themed-icon eagle; the full-colour logo was not regenerated.
Saved generated asset: `app/src/main/res/drawable-nodpi/superking_eagle_mono.png`.

Final prompt:

> Use case: logo-brand. Input image 1 is the Superking brand reference. Create ONE production asset: a simplified monochrome Android themed-icon foreground derived from the front-facing eagle in the reference. Pure opaque BLACK silhouette on genuinely TRANSPARENT background, alpha PNG, square canvas. Preserve the distinctive symmetrical spread wings, downward facing eagle head and compact tail, using fewer bold feather shapes and transparent negative-space cuts in head/wings. Remove ALL lettering, yellow circle, red typography, ornamental background. No white pixels, no solid background, no gray background or checkerboard baked into image, no shadows, no gradients, no mockup, no border, no other objects. Eagle centered optically, occupying approximately 82% canvas width and 58% canvas height, wings entirely inside canvas, crisp clean antialiased silhouette recognizable at 24 pixels. This is an authorized brand-derived application icon, not a new unrelated bird logo.

References: [adaptive icon sizing](https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable), [splash migration](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate).

## Verification — 18 September 2026

- Final debug/test builds succeeded; all 22 JVM tests passed. Android lint has zero errors and 42 warnings (`app/build/branding-final-build.log`).
- Final API 37 emulator run passed all nine checks: two branding/startup tests and seven sales/navigation/state regressions (`app/build/branding-final-ui-tests.log`). An earlier concurrent-build run had two UI timing/focus failures; the complete final rerun passed without weakening tests.
- Generated PNG alpha was checked, and `app/build/branding-preview.png` was visually reviewed. The top row renders native adaptive and tinted monochrome drawables; the lower row illustrates light/dark logo-badge treatment, not a frame capture of the operating-system splash animation.
- Original JPEG and packaged JPEG SHA-256 match: `B2DF532283F1EF40BE2AD1AA0EC320CF843A3314DF1613520BF98691FE676232`.
- Final debug APK installed on the approved Android 15 phone without clearing data. No business records, GPS settings or notification permissions were changed.
- API 23–25 fallback resources compile, but have not been separately verified on a physical legacy device. Launcher-specific themed-icon support and cache refresh behavior can vary.
