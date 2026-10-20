# Glass rendering and launcher widget hosting

Version 0.2.0 uses documented Android app/window and AppWidget APIs. It does not replace or patch SystemUI. Actual OPPO/ColorOS behavior is untested; consult [validation evidence](VALIDATION.md) for the checks that ran.

## Glass boundaries and cost

Dashboard cards share one original 720 × 1440 forest texture, generated off main. Each glass background samples the corresponding scene coordinates; its separate graphics layer uses Android's RenderEffect blur through Compose. Text, buttons, icons and native widget content are drawn separately and remain sharp. Scrolling updates the sample transform without taking screenshots or regenerating/CPU-blurring bitmaps. No new animation timer, network call or sensor is used.

The normal radius is capped at 24 physical pixels in Balanced and 32 in High Quality. API 29/30, software rendering, app Battery Saver and Android power saving use tinted fills and specular borders. High-contrast surfaces use an opaque dark film and skip the obscured scene blur. This is an implementation budget, not a measured handset frame-rate or battery guarantee. [Android RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect).

The app's modal picker/drawer can use public window blur-behind on API 31+, with a WindowManager capability listener and cleanup on dismissal. Android/OEM policy can disable it at runtime. The fill remains readable when it is disabled. The Home dock and icon frames do not read arbitrary wallpaper pixels: their glass treatment uses translucency, highlights and shadow while preserving the actual system wallpaper. RemoteViews are not modified to inject unsupported blur. [Window blur capability](https://developer.android.com/reference/android/view/WindowManager#isCrossWindowBlurEnabled()).

## Using hosted widgets

1. Open Sid Home and tap **Widgets**, or dashboard **Glass widgets → Add widgets inside Sid Home**.
2. Search installed providers in the current user profile. Select one; Android asks to create the widget and allow access when binding has not already been approved. Sid Glass OS does not silently obtain this access.
3. Finish the provider's setup if required. A canceled bind/setup releases the allocated widget ID and creates no permanent page. Interrupted setup remains cancellable from Home.
4. Swipe past the app pages. Each widget has a full-width page; supported providers expose **Shorter widget / Taller widget**. Resizing sends platform size options, including responsive sizes on Android 12+. Provider minimum/maximum dimensions are honored; a widget that cannot fit offers a clear message.
5. Use **Move earlier / Move later** to order widget pages. **Remove widget** confirms removal, releases its system ID and keeps the provider app installed.

There are up to 12 hosted widgets. Android AppWidgetHostView renders native RemoteViews and dispatches normal widget interactions. Listening follows the Home activity's visible lifetime. IDs, sizes, page order and pending bind/configuration state are app-private; appearance reset preserves them. Missing providers show an unavailable state or are removed on the system's removal callback. Work/private-profile widgets, mixed icon/widget grids, drag handles and third-party pin-request intake are not implemented. Existing pin buttons still work through compatible default launchers; use Sid Home's picker when its pin flow is unavailable.

The manifest declares BIND_APPWIDGET as specified by Android's host contract. A regular install does not receive this signature permission. The host calls bindAppWidgetIdIfAllowed and, when needed, Android's ACTION_APPWIDGET_BIND consent flow. A narrowly documented manifest lint exception covers this host use; no permission check is bypassed. Required provider setup is brokered through AppWidgetHost's public configuration API. [Android widget-host contract](https://developer.android.com/develop/ui/views/appwidgets/host).

## Test scope and remaining device checks

JVM tests cover effect gating/radius caps, crop alignment, widget ID normalization/capacity, size limits and order. Emulator tests use real system consent, real native clock views and a separate test-only configurable provider. That provider uses only platform Java because instrumentation dependencies from the target APK are unavailable when the test APK runs as an independent provider process. Production APKs contain none of those fixture components.

Emulator results do not establish ColorOS GPU blur support, gesture/Recents behavior, Home-role persistence, provider compatibility across every installed app, reboot/work-profile recovery, live-wallpaper lifecycle, media revocation, thermal behavior or battery use. The phone's actual Android/API level and reported firmware build must still be measured. Protected notification shade, secure lock-screen layout, dialer/Settings reskins and arbitrary Theme Store package import remain outside this app's supported scope. See the [OPPO acceptance checklist](DEVICE_TESTS.md).
