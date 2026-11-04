pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        jcenter()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}
rootProject.name = "DolaDown"
include(":app", ":virtualapp-engine:lib")
project(":virtualapp-engine:lib").projectDir = file("virtualapp-engine/lib")
