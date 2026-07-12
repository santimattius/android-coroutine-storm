plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.santimattius.basic.skeleton.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Android 15 tightened "stopped state" semantics (apps only leave it via direct/indirect
        // user action), so the profileinstaller broadcast Macrobenchmark sends right after its
        // own force-stop between COLD iterations can be dropped by the platform on some devices.
        // Tolerate that instead of failing the whole benchmark run.
        testInstrumentationRunnerArguments["androidx.benchmark.dropShaders.throwOnFailure"] = "false"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    flavorDimensions += "scenario"
    productFlavors {
        create("calm") {
            dimension = "scenario"
            buildConfigField("String", "TARGET_APP_ID", "\"com.santimattius.basic.skeleton\"")
        }
        create("storm") {
            dimension = "scenario"
            buildConfigField("String", "TARGET_APP_ID", "\"com.santimattius.basic.skeleton.storm\"")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

detekt {
    config.setFrom("${project.rootDir}/config/detekt/detekt.yml")
    baseline = file("$rootDir/detekt-baseline.xml")
    autoCorrect = true
}

dependencies {
    implementation(libs.test.ext)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.uiautomator)
}
