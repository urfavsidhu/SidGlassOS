# Wallpapers and widgets

## Wallpaper behavior

The forest is original Android Canvas artwork. The renderer caches a bounded bitmap for the gradient sky, moon, mist and pine layers; only particles are redrawn on animated frames. Dashboard previews render a still image off the main thread. The launcher reveals the actual selected Android wallpaper through its translucent window. No reference photograph is shipped.

The live service uses the documented [WallpaperService.Engine](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine) surface and visibility callbacks. Rendering runs on a dedicated HandlerThread. It removes frame callbacks when hidden, the screen turns off, the surface is destroyed, or the engine is destroyed, and releases the cached bitmap at surface destruction. High Quality caps at 30 fps; Balanced caps at 24 fps and 40 particles. Battery Saver, Android power saving, and Reduced motion produce a still frame. Zero intensity or zero particles also removes the animation timer. Parallax follows launcher-supplied page offsets, without sensors; launcher support varies.

Static wallpaper buttons explicitly choose home, lock, or both. [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager) policy checks, bitmap creation and setBitmap run off the main thread; success appears only after the API returns. SET_WALLPAPER is the ordinary manifest permission for that operation. The live-wallpaper action opens Android's own preview/confirmation flow and falls back to the system live-wallpaper chooser if necessary. The service is protected by BIND_WALLPAPER, which is held by the system host rather than requested from the user.

The available live-wallpaper home/lock choices are controlled by Android and ColorOS. This app cannot impose a custom secure lock-screen clock, notification card or music layout. The in-app preview is labeled as a preview.

## Native widgets

Four resizable [Android App Widgets](https://developer.android.com/develop/ui/views/appwidgets/overview) use native RemoteViews layouts, a shared original glass background and adjustable opacity/text color. Android's [TextClock](https://developer.android.com/reference/android/widget/TextClock) updates clock/date text and follows the device's 12/24-hour setting.

| Widget | Behavior |
| --- | --- |
| Digital clock | Time/date; opens the installed alarms app |
| Battery | Current percentage, charging state and progress bar; tapping refreshes |
| Music | Optional real track/artist/artwork and supported transport actions; always offers a player shortcut |
| At a glance | Time/date, battery summary, and selected Calendar/Settings/Sid Glass OS shortcut |

The music widget starts with a permission-free empty state. **Connect media** in the app explains optional Android notification access before opening the official settings. Once the user enables it, a local session bridge provides current metadata/artwork and the actions advertised by the player. The widget clears stale metadata when access/session disappears. No track data is written to storage; artwork is bounded again to 128px for widget transactions. Callback changes refresh only music widgets. See [media privacy and behavior](MEDIA.md).

Battery and information cards request Android's minimum periodic update interval of 30 minutes, which the system may defer. Widgets also refresh when added, resized, or styling changes. There is no background polling service, no alarm schedule and no forced process startup at boot. Clock and music widgets do not request periodic updates. Battery readings are labeled as periodically refreshed in the widget setup screen; they are not real-time telemetry.

Pin buttons use the launcher's supported requestPinAppWidget confirmation. When pinning is unsupported, the screen gives manual placement instructions. These widgets can be hosted by the original OPPO launcher or another AppWidget host. Sid Home now also hosts its own and other installed providers through Widgets, with Android consent and required configuration. It preserves widget IDs, page order and height, and releases IDs on cancellation/removal. See [host behavior and limits](GLASS_AND_HOSTING.md). Shortcut launch failures show a visible message instead of claiming success. The information shortcut is stored only in app-private preferences; WidgetRefresh.resetShortcut supports resetting it with dashboard settings.

## Verification status and device checklist

The frame policy has JVM unit tests for mode limits, reduced motion, power saving and particle bounds. Check the project's build report for actual compilation/test results. No OPPO device or emulator result is implied by the implementation.

- Apply static wallpaper to home, lock, then both; verify the system image and cancel/restricted-policy behavior.
- Preview and cancel live wallpaper; apply it using ColorOS's available destination choices.
- Switch apps, turn the screen off, rotate the preview, change home pages, destroy/recreate the surface and inspect rendering/performance with Android Studio.
- Enable each performance mode, Reduced motion and system power saving. Verify still-frame modes have no continuing frame callbacks.
- Set particles/intensity to zero; verify the renderer stops scheduling frames and responds to later setting changes.
- Add, resize and remove all four widgets in the OPPO launcher. Verify clock/date in both time formats and across midnight/time-zone changes.
- Change widget opacity/text color, confirm installed cards refresh, and test all information shortcuts.
- Charge/unplug the device; tap battery to refresh. Verify periodic reading behavior after background/reboot without expecting immediate real-time updates.
- Test missing music/alarms/calendar apps, pin cancellation, restricted launcher pinning, low memory and uninstall/revert behavior.
