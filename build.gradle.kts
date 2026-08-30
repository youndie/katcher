plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.pluginSerialization) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(jvmLibs.plugins.jib) apply false
    alias(jvmLibs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kspPlugin) apply false
    alias(libs.plugins.atomicfu) apply false
    alias(libs.plugins.sborkaKmp) apply false
    alias(libs.plugins.sborkaJvm) apply false
    alias(libs.plugins.sborkaLint) apply false
    alias(libs.plugins.sborkaPublish) apply false
}

// The group, the version, the ktlint wiring and a `repositories { }` block were all handed out from
// a `subprojects { }` block here. They are `gradle.properties` now — `sborka.group` and `version` —
// applied per module by `ru.workinprogress.sborka.base`.
//
// The repositories went to `settings.gradle.kts`. The comment that stood over them said they had to
// be declared per project because project repositories override the settings ones — which is true,
// and is exactly what `FAIL_ON_PROJECT_REPOS` exists to refuse: a build resolving one coordinate
// from different places depending on which module asked.
//
// The ktlint version was pinned inline with a `renovate:` annotation over it. `sborka.lint` pins the
// same 1.8.0 for the whole portfolio, so the number — and the annotation that kept it fresh — now
// live in sborka rather than here.
