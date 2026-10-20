# Assets and dependencies

The forest, app mark, glass frames and procedural drawing code are created for this project. `references/` contains user-provided research images; they are excluded from Android app resources.

Category glyphs come from Material Icons via the AndroidX Compose Material Icons dependency, under Apache License 2.0. They are not copied application icons. Installed app icons are obtained at runtime through PackageManager and remain associated with their original applications.

Build/runtime dependencies include AndroidX, Jetpack Compose, Kotlin and Kotlin coroutines. Their respective copyright notices/licenses apply. Official source/license information: [AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt), [Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt), [coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt), [Material Icons](https://github.com/google/material-design-icons/blob/master/LICENSE).

Gradle and Android SDK tools are downloaded into ignored `.tools/` for local development and are not application assets. Android SDK use requires its separate license agreement.
