import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // google-services.json에서 FCM 발신자 ID·API 키를 읽어 리소스로 굽는다.
    // 파일이 없으면 이 플러그인이 빌드를 세운다 — 콘솔에서 받아 app/에 두면 된다.
    alias(libs.plugins.google.services)
}

/**
 * 서명 정보는 저장소에 안 들어간다(`keystore.properties`는 gitignore). 그래서 **없을 수도 있다고
 * 보고 읽는다** — 파일이 없으면 릴리스도 서명 없이 빌드된다. 없을 때 빌드를 세우면 키를 가진
 * 사람 한 명 말고는 아무도 `assembleRelease`를 못 돌리게 된다.
 *
 * 서명이 실제로 붙었는지는 산출물로 확인한다: 붙으면 `app-release.apk`, 안 붙으면
 * `app-release-unsigned.apk`로 파일 이름이 갈린다.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.rta.dignify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rta.dignify"
        // iOS와 같은 지면을 그리려면 Compose가 필요하고, 26 미만은 이제 실사용 표본이 없다.
        minSdk = 26
        targetSdk = 35
        // Play는 업로드마다 새 versionCode를 요구한다. 한 번 쓴 값은 그 업로드를 지워도 다시 못 쓴다.
        // versionName과 따로 노는 게 정상이다 — 이건 카운터고, 유저에게 보이는 건 versionName이다.
        //
        // 백엔드가 UA(`dignify/<versionCode>`)에서 이 값을 주워 담아 푸시를 버전별로 갈라 쏜다.
        versionCode = 4
        versionName = "1.0.0"

        // 구글이 발급하는 ID 토큰의 aud가 이 값이고, 백엔드(GOOGLE_CLIENT_ID)가 같은 값으로 대조한다.
        // 두 값이 어긋나면 로그인 창은 뜨는데 서버가 aud 불일치로 거부한다.
        //
        // 안드로이드 앱인데 "웹" 클라이언트 ID인 게 맞다. Android 클라이언트 ID를 넣으면
        // GetSignInWithGoogleOption이 [28444] "Developer console is not set up correctly"로 죽는다
        // (실제로 한 번 겪었다 — 지문·패키지명이 다 맞아도 타입이 틀리면 이 에러다).
        //
        // 그렇다고 Android 클라이언트가 필요 없는 건 아니다. 코드에서 참조하지는 않지만
        // GMS가 호출한 앱을 패키지명+SHA-1로 대조하는 데 쓰므로 콘솔에 함께 등록돼 있어야 한다.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"460750160818-s5ipb30p4ulpjnvkir1h7qgsjt0cje1e.apps.googleusercontent.com\"",
        )
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 위에서 키를 못 읽었으면 null이고, 그럼 서명 없이 나간다.
            signingConfig = signingConfigs.findByName("release")
            // ponytail: R8을 끈 채로 첫 출시한다. 켜면 Ktor·kotlinx.serialization·Compose 쪽
            // 규칙을 손봐야 하는데, 그 디버깅을 출시일에 할 이유가 없다. APK 15.8MB면 충분히 작다.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.coil.compose)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.posthog.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
