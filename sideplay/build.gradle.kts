plugins { id("com.android.library") }

android {
    namespace = "com.oasisfeng.island.sideplay"

    compileSdk = 35

    defaultConfig {
        minSdk = 27
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
        }
    }
    lint {
        targetSdk = 35
    }
    testOptions {
        targetSdk = 35
    }
}
