plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "com.fr.husi.plugin.mieru"
    }
    namespace = "com.fr.husi.plugin.mieru"
}

setupPlugin("mieru")
