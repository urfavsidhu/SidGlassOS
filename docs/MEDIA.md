# Optional local music connection

The Control Center, lock-screen **preview**, and native music widget can show a compatible player's title/artist/artwork and the transport actions it advertises. These surfaces do not alter Android's lock screen, notification shade or original music app.

**Connect media** first explains the scope, then opens the documented Android notification-listener access settings. The user must enable **Glass music connection** in that system screen. Installing/opening the app does not grant access. Android's notification-posting permission is a different grant used only by the local notification test.

Android exposes cross-app active sessions to an enabled notification listener or a privileged app with `MEDIA_CONTENT_CONTROL`; Sid Glass OS uses only the user-granted listener route. [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName))

## Data handling

- Never enumerate, mirror, log, upload or store notification contents. Notification callbacks ignore their payloads.
- Android12/API31+ listener default-filter metadata is present and empty, requesting no notification-content types. Older Android versions do not support that filter; callbacks still ignore payloads. An enabled listener separately authorizes media-session queries. [Listener filter documentation](https://developer.android.com/reference/android/service/notification/NotificationListenerService#META_DATA_DEFAULT_FILTER_TYPES)
- Keep only current media-session state in process memory. Bound album-art dimensions to protect UI memory/widget Binder transactions. Never recycle bitmaps owned by the music application.
- Use callbacks, not a polling timer. Release session/controller callbacks and clear state on listener disconnection, access revocation and service destruction. Refresh only music widgets when session state changes.
- This is an optional OS-managed listener, with no foreground service, wake lock or network permission. Declining keeps the Open player shortcut available.

## Behavior and reversal

Start playback in a player after granting access. The app favors an active playing session; players can omit artwork or specific actions. Controls reflect supported actions and observed playback state; dispatching a command does not imply that playback has changed. A missing/disconnected session is an explicit empty state.

**Manage access** returns to Android's grant screen. Turn off Glass music connection to stop session access. Resetting appearance/layout preferences does not revoke OS grants; revoke separately or uninstall the application.

Device tests must cover permission declined/revoked while cards/widgets are visible, no-session state, track changes, player death, artwork missing, actions missing, switching players and reboot. JVM/compilation checks cannot establish that a particular OPPO firmware or music app delivers these sessions correctly.
