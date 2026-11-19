# Mobile applications

- `android/`: Kotlin and Jetpack Compose application scaffold.
- `ios/`: Swift and SwiftUI application scaffold.

These directories reserve the native project layout; runnable projects, SDK/plugin versions, the Gradle Wrapper, and Xcode project configuration will be added during implementation. The iOS folder names follow the requested layout; the final app display name is Irada.

The app identifier on both platforms is `com.ctxaware.irada`. During Android Stage 0, use the [Gradle properties](android/gradle.properties) for the application ID, namespace (same as the application ID), and SDK levels. When creating the iOS app target, assign [Irada.xcconfig](ios/Irada.xcconfig) as its base configuration for Debug and Release and inherit its bundle identifier.
