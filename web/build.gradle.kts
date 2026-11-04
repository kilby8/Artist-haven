plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// The web app: the engine compiled to JavaScript plus a hand-written UI in site/. It runs in Edge
// or Chrome and installs as an app on Windows (Surface Pro) with full pen pressure and tilt.
kotlin {
    js {
        browser()
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        jsMain.dependencies {
            implementation(project(":engine"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// build/site is the whole static site: the UI files plus the compiled engine under engine/.
val assembleSite by tasks.registering(Sync::class) {
    group = "build"
    description = "Builds the static web app into build/site."
    dependsOn("jsBrowserProductionLibraryDistribution")
    from("site")
    from(layout.buildDirectory.dir("dist/js/productionLibrary")) {
        into("engine")
    }
    into(layout.buildDirectory.dir("site"))
}
