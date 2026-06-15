plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.swordfish.lemuroid.cores"
}

dependencies {
    implementation(kotlin(deps.libs.kotlin.stdlib))
}
