plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))

    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.dagger.core)
    implementation(deps.libs.kotlinxCoroutinesAndroid)

    ksp(deps.libs.androidx.room.compiler)
    ksp(deps.libs.dagger.compiler)
}

android {
    resourcePrefix("libretrodb_")
    kotlinOptions {
    }
    namespace = "com.swordfish.lemuroid.metadata.libretrodb"
}
