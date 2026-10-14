import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Debug backend URL. Override per machine: ./gradlew installDebug -Pgrandma.devBackendUrl=http://192.168.1.20:3000/
val devBackendUrl: String =
    (project.findProperty("grandma.devBackendUrl") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: "http://10.0.2.2:3000/"

// Cleartext is only permitted for hosts listed in the debug network security config,
// so a dev URL pointing anywhere else would fail at runtime with an opaque error.
run {
    val devUri = URI(devBackendUrl)
    if (devUri.scheme == "http") {
        val nscFile = file("src/debug/res/xml/network_security_config.xml")
        val host = devUri.host
        if (!nscFile.readText().contains(">$host<")) {
            throw GradleException(
                "grandma.devBackendUrl host '$host' is not allowed for cleartext in " +
                    "${nscFile.relativeTo(rootDir)}. Add a <domain>$host</domain> entry or use https."
            )
        }
    }
}

android {
    namespace = "com.grandma.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.grandma.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_BASE_URL", "\"$devBackendUrl\"")
        }
        release {
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://api.example.invalid/\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all { test ->
                test.testLogging {
                    showStandardStreams = true
                    events("passed", "failed", "skipped")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
