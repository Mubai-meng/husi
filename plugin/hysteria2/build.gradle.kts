plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "com.fr.husi.plugin.hysteria2"
    }
    namespace = "com.fr.husi.plugin.hysteria2"
}

setupPlugin("hysteria2")
