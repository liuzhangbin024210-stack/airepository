import java.io.File
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.majiang.counter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.majiang.counter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        // 无本地会话时是否自动登录内置管理员（发布版默认关闭，见 debug 覆盖）
        buildConfigField("Boolean", "AUTO_ADMIN_LOGIN", "false")
    }

    buildTypes {
        debug {
            // 便于真机/模拟器测试：启动即管理员会话（仍可在设置中退出）
            buildConfigField("Boolean", "AUTO_ADMIN_LOGIN", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    sourceSets {
        named("androidTest") {
            // 金帧回归：直接使用仓库根目录 picture/*.png，与 picture/README.md 帧清单一致
            assets.srcDir("${rootProject.projectDir}/picture")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(project(":vision-yolo"))
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    val camerax = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("org.tensorflow:tensorflow-lite:2.15.0")

    // HUD「剩 NN 张」：ML Kit 中文识别（随 APK 打包模型，离线可用）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
    // JVM 单元测试需真实 JSON 实现；android.jar 内的 org.json 为桩，会导致 JSONObject.put 等抛「not mocked」。
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

/**
 * Windows：Gradle 为测试 Worker 拼出的命令行里，`-Djava.library.path=C:\\Program Files\\...` 未整体加引号，
 * 子 JVM 会把 `Files\\Eclipse...` 误判为主类名（ClassNotFoundException: Files\\Eclipse）。
 * 通过 CommandLineArgumentProvider 让该参数作为「单个 argv」传入，并固定使用当前 Gradle JVM 的 jdk/bin。
 */
tasks.withType<Test>().configureEach {
    val home = System.getProperty("java.home") ?: return@configureEach
    val winJava = File(home, "bin/java.exe")
    val unixJava = File(home, "bin/java")
    when {
        winJava.isFile -> executable = winJava.absolutePath
        unixJava.isFile -> executable = unixJava.absolutePath
    }
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Djava.library.path=${File(home, "bin").absolutePath}")
        },
    )
}

/**
 * 校验 `model_manifest.json` 中引用的策略 / 牌面 TFLite 文件名在 assets 目录是否存在（缺失则告警，不阻断构建）。
 */
tasks.register("verifyMlManifest") {
    group = "verification"
    description = "校验 assets/ml 下 manifest 与 policy/tile 文件是否同名存在"
    doLast {
        val base = file("src/main/assets/ml/xuezhan_mahjong_default")
        val mf = File(base, "model_manifest.json")
        if (!mf.isFile) {
            logger.lifecycle("verifyMlManifest: 无 manifest，跳过")
            return@doLast
        }
        val text = mf.readText(Charsets.UTF_8)
        fun quotedValue(key: String): String? {
            val re = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            return re.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun check(field: String, fileName: String?) {
            if (fileName.isNullOrEmpty()) return
            val f = File(base, fileName)
            if (!f.isFile) {
                logger.warn("verifyMlManifest: manifest 声明 $field=$fileName 但文件不存在: ${f.absolutePath}")
            }
        }
        check("policyFile", quotedValue("policyFile"))
        check("tileClassifierFile", quotedValue("tileClassifierFile"))
        check("tileDetectorFile", quotedValue("tileDetectorFile"))
    }
}

tasks.named("preBuild").configure { dependsOn("verifyMlManifest") }
