@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
}

setupApp()

android {
    defaultConfig {
        splits.abi {
            reset()
            include(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
                "x86",
            )
        }
        ndkVersion = "28.2.13676358"
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        buildConfig = false
    }
    packaging {
        // protobuf 工具链带入的 edition 特性描述符，两个 runtime 依赖各打一份
        // 且路径不同无法自动去重；运行时不读取（proto3 消息不走特性解析），
        // 排除以去掉 APK 里的 core/ 与 java/core/ 重复资源。
        resources.excludes += "**/java_features_proto-descriptor-set.proto.bin"
    }
    namespace = "com.fr.husi"

}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.room.runtime)
    debugImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
