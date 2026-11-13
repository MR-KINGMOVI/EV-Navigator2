plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.evnavigator.iran.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

kapt {
    correctErrorTypes = true
    javacOptions {
        option("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    // Excludes removed per audit rule 3 and item 16
}
