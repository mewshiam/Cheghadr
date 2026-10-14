plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ir.cheghadr.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.cheghadr.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "fa")
    }

    // Release signing is driven by environment variables so no secrets live in the repo:
    //   CHEGHADR_KEYSTORE_PATH, CHEGHADR_KEYSTORE_PASSWORD,
    //   CHEGHADR_KEY_ALIAS, CHEGHADR_KEY_PASSWORD
    // See docs/BUILD.md and .github/workflows/release.yml
    signingConfigs {
        val keystorePath = System.getenv("CHEGHADR_KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CHEGHADR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CHEGHADR_KEY_ALIAS")
                keyPassword = System.getenv("CHEGHADR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!System.getenv("CHEGHADR_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }
    lint {
        // Release builds should not be blocked by lint opinions; CI runs lint separately.
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests.all { it.useJUnit() }
    }
}

dependencies {
    // Project modules
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:notifications"))
    implementation(project(":core:widgets"))

    implementation(project(":feature:home"))
    implementation(project(":feature:markets"))
    implementation(project(":feature:watchlist"))
    implementation(project(":feature:assetdetails"))
    implementation(project(":feature:converter"))
    implementation(project(":feature:alerts"))
    implementation(project(":feature:portfolio"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}
