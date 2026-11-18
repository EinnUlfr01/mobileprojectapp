import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.huflit.doanappgiaodoan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.huflit.doanappgiaodoan"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    // Chat realtime với server (ASP.NET Core SignalR)
    implementation("com.microsoft.signalr:signalr:8.0.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
// Mỗi lần bấm Run: tự nối cổng 5090 của máy ảo / điện thoại (cắm cáp) về API trên máy tính,
// khỏi phải gõ "adb reverse tcp:5090 tcp:5090" bằng tay.
val sdkDirForAdb: String = run {
    val f = rootProject.file("local.properties")
    val p = Properties()
    if (f.exists()) f.inputStream().use { p.load(it) }
    p.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: ""
}
val adbReverse = tasks.register("adbReverse") {
    val sdkDir = sdkDirForAdb
    notCompatibleWithConfigurationCache("Chạy lệnh adb trên máy")
    doLast {
        val exe = if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb"
        val adb = File(sdkDir, "platform-tools/$exe")
        if (!adb.exists()) return@doLast
        fun sh(vararg args: String): String = try {
            val p = ProcessBuilder(adb.path, *args).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(); out
        } catch (e: Exception) { "" }
        sh("devices").lines().drop(1).map { it.trim() }.filter { it.endsWith("device") }
            .map { it.split(Regex("\\s+"))[0] }
            .forEach { serial -> sh("-s", serial, "reverse", "tcp:5090", "tcp:5090"); println("adb reverse 5090 -> $serial") }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(adbReverse) }
