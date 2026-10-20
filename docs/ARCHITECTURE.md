# Project architecture

The Android Studio project lives at the workspace root. A single `:app` build module keeps initial builds small; Kotlin packages separate responsibilities and can be extracted into Gradle libraries later.

```text
app/src/main/java/com/sidglassos/
  core/design/       Compose theme, glass components, original symbols
  core/storage/      DataStore settings, launcher layout and icon assignments
  core/platform/     Safe intent launching and device facts
  features/dashboard/  Navigation, customization dashboard, settings, previews
  features/launcher/   HOME activity, launcher package catalog and icon cache
  features/controlcenter/ In-app controls and Quick Settings entry tile
  features/media/       Optional local media sessions, permission disclosure and shared music card
  features/wallpapers/  Shared forest renderer, live service, static application
  features/widgets/    Native RemoteViews providers
```

## Boundaries

`DashboardActivity` is the normal app entry; `LauncherActivity` is a separately declared HOME entry. Starting the dashboard never silently changes the home role. Launcher selection uses `RoleManager.ROLE_HOME`, with official Settings as a fallback.

Preferences are local DataStore values. App catalog queries only launcher activities; no broad `QUERY_ALL_PACKAGES`, usage access, contacts, location, or storage access. Original application components are used for launching; icon styling is confined to Sid Glass OS.

The live wallpaper owns a visible-surface render loop with bounded FPS and Canvas assets. Static wallpapers are generated off the main thread and applied only after an explicit action. Widgets use documented AppWidget/RemoteViews APIs, not a persistent update service.

The control center is an Activity, with an optional system Quick Settings entry tile. Restricted system changes open official Settings screens. No overlay, notification listener, accessibility service or fake keyguard is enabled as a side effect of installation.

## Phased verification

1. Environment inventory and cited compatibility report; SDK/Gradle preparation.
2. Compile the base app and design system before feature integration.
3. Compile launcher and run layout/persistence logic unit tests.
4. Compile wallpapers/widgets; document device-only lifecycle tests.
5. Compile in-app controls and tile; verify the manifest's permission surface.
6. Integrate dashboard and appearance controls.
7. Run unit tests, Android lint and APK assembly; report connected-device testing separately.

Build output is evidence of compilation only. OPPO firmware behavior, launcher gestures, wallpaper picker choices, thermal behavior and frame pacing require the actual handset.
