plugins { id("com.android.library") }

android {
    namespace = "com.oasisfeng.island.sideplay"

    compileSdk = 35

    defaultConfig {
        minSdk = 27
        targetSdk = 31
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
        }
    }
}
