import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

fun propertiesOf(file: File): Properties = Properties().apply {
    if (file.isFile) file.inputStream().use(::load)
}

val localProperties = propertiesOf(rootProject.file("local.properties"))
val dotEnv = propertiesOf(rootProject.file(".env"))

// Ambiente da API: -PAPI_ENV vence o .env; sem nenhum dos dois, desenvolvimento local.
val apiEnv = (providers.gradleProperty("API_ENV").orNull ?: dotEnv.getProperty("API_ENV") ?: "local")
    .trim()
    .lowercase()
    .also { require(it == "local" || it == "prod") { "API_ENV deve ser 'local' ou 'prod', mas era '$it'" } }

// local.properties é só para o caminho do SDK: a URL em dois arquivos confunde.
require(localProperties.getProperty("API_BASE_URL") == null) {
    "API_BASE_URL não é lida de local.properties. Defina API_ENV e API_BASE_URL_LOCAL/_PROD " +
        "em mobile/.env (copie mobile/.env.example)."
}

// Precedência: -PAPI_BASE_URL, .env do ambiente escolhido, emulador.
val apiBaseUrl = (
    providers.gradleProperty("API_BASE_URL").orNull
        ?: dotEnv.getProperty(if (apiEnv == "prod") "API_BASE_URL_PROD" else "API_BASE_URL_LOCAL")
        ?: "http://10.0.2.2:8080"
    ).trim()

require(apiBaseUrl.matches(Regex("https?://[^\\s\"\\\\]+"))) {
    "API_BASE_URL deve ser uma URL HTTP(S) válida, mas era '$apiBaseUrl'"
}

if (apiEnv == "prod") {
    // Em produção a API sai pelo Nginx em 443 (docs/deploy/https-producao.md): só a origem HTTPS.
    val uri = runCatching { URI(apiBaseUrl) }.getOrNull()
    requireNotNull(uri) { "API_BASE_URL de produção não é uma URI válida: '$apiBaseUrl'" }
    require(uri.scheme == "https") { "API_BASE_URL de produção deve usar https://, mas era '$apiBaseUrl'" }
    require(uri.port == -1) { "API_BASE_URL de produção não pode ter porta: '$apiBaseUrl'" }
    require(uri.path.isNullOrEmpty()) {
        "API_BASE_URL de produção deve conter só a origem, sem caminho nem barra final: '$apiBaseUrl'"
    }
    require(uri.query == null && uri.fragment == null) {
        "API_BASE_URL de produção não pode ter query nem fragmento: '$apiBaseUrl'"
    }
}

// Release nunca sai com HTTP: cleartext só existe no debug.
val verifyReleaseApiBaseUrl = tasks.register("verifyReleaseApiBaseUrl") {
    val url = apiBaseUrl
    doLast {
        check(url.startsWith("https://")) {
            "O build release exige API_BASE_URL HTTPS, mas era '$url'. Use API_ENV=prod."
        }
    }
}
tasks.matching { it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease") || it.name == "preReleaseBuild" }
    .configureEach { dependsOn(verifyReleaseApiBaseUrl) }

android {
    namespace = "com.controlerotina.mobile.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.controlerotina.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
