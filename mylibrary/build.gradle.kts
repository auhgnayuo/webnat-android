plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.auhgnayuo.webnat"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

mavenPublishing {
    coordinates("io.github.auhgnayuo", "webnat", "1.0.0")

    pom {
        name.set("Webnat")
        description.set("A lightweight WebView-Native bridge library supporting raw messages, broadcast, and RPC.")
        url.set("https://github.com/auhgnayuo/webnat-android")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("auhgnayuo")
                name.set("Auhgnayuo")
                url.set("https://github.com/auhgnayuo")
            }
        }

        scm {
            url.set("https://github.com/auhgnayuo/webnat-android")
            connection.set("scm:git:git://github.com/auhgnayuo/webnat-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/auhgnayuo/webnat-android.git")
        }
    }

}
