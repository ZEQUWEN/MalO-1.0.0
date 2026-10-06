// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

// Dedicated assembleRelease task configuration to simplify the build process
// for CI/CD environments like Railway, ensuring dependencies are cached properly.
tasks.register("assembleRelease") {
  group = "build"
  description = "Dedicated assembleRelease task for CI/CD environments like Railway to assemble release APK with dependency caching."
  dependsOn(":app:assembleRelease")
}
