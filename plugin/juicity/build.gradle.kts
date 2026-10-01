plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "com.fr.husi.plugin.juicity"
    }
    namespace = "com.fr.husi.plugin.juicity"
}

setupPlugin("juicity")
