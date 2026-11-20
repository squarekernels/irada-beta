import java.net.URI

plugins {
  alias(libs.plugins.ksp)
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.ctxaware.irada"
  compileSdk = providers.gradleProperty("irada.compileSdk").get().toInt()
  defaultConfig {
    applicationId = providers.gradleProperty("irada.applicationId").get()
    minSdk = providers.gradleProperty("irada.minSdk").get().toInt()
    targetSdk = providers.gradleProperty("irada.targetSdk").get().toInt()
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val configuredPreviewUrl = providers.gradleProperty("irada.previewBaseUrl").orElse("").get()
  if (configuredPreviewUrl.isNotEmpty()) {
    val uri = URI(configuredPreviewUrl)
    require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
    require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in listOf("127.0.0.1", "localhost"))) {
      "Preview URL must use HTTPS, or HTTP loopback for debug builds."
    }
  }
  buildTypes {
    debug {
      buildConfigField("String", "PREVIEW_BASE_URL", "\"${configuredPreviewUrl.ifEmpty { "http://127.0.0.1:3000/" }}\"")
    }
    release {
      val releaseUrl = configuredPreviewUrl.takeIf { it.startsWith("https://") }.orEmpty()
      buildConfigField("String", "PREVIEW_BASE_URL", "\"$releaseUrl\"")
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(21)
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
  implementation(libs.okhttp)
  implementation(libs.serialization.json)
  testImplementation(libs.mockwebserver)
  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.datastore.preferences)
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

// Export schemas for future migrations; never discard a pending draft on upgrade.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
