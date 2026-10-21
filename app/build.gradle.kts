import java.util.Properties
import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val version = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val signingPath = System.getenv("MISTGECKO_KEYSTORE")
android {
    namespace = "com.mistgecko"
    compileSdk = 35
    defaultConfig {
        applicationId = providers.gradleProperty("testApplicationId").getOrElse("com.mistgecko")
        minSdk = 29; targetSdk = 35
        versionCode = version.getProperty("versionCode").toInt()
        versionName = version.getProperty("versionName")
        testInstrumentationRunner = "com.mistgecko.PipelineInstrumentation"
    }
    signingConfigs {
        if (signingPath != null) create("distribution") {
            storeFile = file(signingPath)
            storePassword = System.getenv("MISTGECKO_STORE_PASSWORD")
            keyAlias = System.getenv("MISTGECKO_KEY_ALIAS")
            keyPassword = System.getenv("MISTGECKO_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (signingPath != null) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    flavorDimensions += "engine"
    productFlavors {
        create("demo") { manifestPlaceholders["appLabel"] = "MistGecko Demo"; dimension = "engine"; applicationIdSuffix = ".demo"; versionNameSuffix = "-demo" }
        create("ai") {
            manifestPlaceholders["appLabel"] = "MistGecko AI"; dimension = "engine"
            ndk { abiFilters += "arm64-v8a" }
        }
    }
    androidResources { noCompress += "onnx" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { "aiImplementation"("com.microsoft.onnxruntime:onnxruntime-android:1.23.2") }
val verifyBundledModel by tasks.registering {
    doLast {
        val lock = groovy.json.JsonSlurper().parse(rootProject.file("model.lock.json")) as Map<*, *>
        val model = file("src/ai/assets/models/lama_fp32.onnx")
        check(model.exists() && model.length() == (lock["size"] as Number).toLong()) { "Run python3 scripts/prepare_model.py before building AI" }
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { input -> val buffer = ByteArray(65536); while(true) { val n = input.read(buffer); if(n < 0) break; digest.update(buffer,0,n) } }
        check(digest.digest().joinToString("") { "%02x".format(it) } == lock["sha256"]) { "Bundled model checksum mismatch" }
    }
}
tasks.matching { it.name == "preAiDebugBuild" || it.name == "preAiReleaseBuild" }.configureEach { dependsOn(verifyBundledModel) }
tasks.matching { it.name == "preAiReleaseBuild" }.configureEach {
    doFirst { check(signingPath != null) { "Release requires MISTGECKO_* signing environment variables; see docs/RELEASING.md" } }
}
