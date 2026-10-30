plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "ru.dj.mishki"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.dj.mishki"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        val vkSecret = System.getenv("VKID_CLIENT_SECRET") ?: ""
        manifestPlaceholders["VKIDClientID"] = "54866854"
        manifestPlaceholders["VKIDClientSecret"] = vkSecret
        manifestPlaceholders["VKIDRedirectHost"] = "vk.ru"
        manifestPlaceholders["VKIDRedirectScheme"] = "vk54866854"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.vk.id:vkid:2.7.3")
}
