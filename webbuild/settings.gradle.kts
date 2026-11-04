// A build with only the engine and the web app, so the web app can be built without the Android
// SDK or plugin:  ./gradlew -p webbuild :web:assembleSite
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "Artist-haven-web"
include(":engine")
project(":engine").projectDir = file("../engine")
include(":web")
project(":web").projectDir = file("../web")
