plugins {
  alias(libs.plugins.kotlin.jvm)
  `kotlin-dsl`
  id("com.redhat.devtools.toolbox.packaging")
  id("com.redhat.devtools.toolbox.install")
  `java-library`
}

// Note, the `group` value is used as a provider ID
// when handling the URLs like `jetbrains://gateway/provider.ID`
group = "com.redhat.devtools.toolbox"
version = "0.0.1"

kotlin {
  jvmToolchain(21)
}

dependencies {
  compileOnly(libs.bundles.toolbox.plugin.api)
  compileOnly(libs.coroutines.core)
}

// Known issue with kotlin 2.1.0 when using MutableStateFlow, please remove
// once https://youtrack.jetbrains.com/issue/KT-73951 is released and you upgrade version.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  compilerOptions {
    freeCompilerArgs.add("-Xdisable-phases=ConstEvaluationLowering")
  }
}
