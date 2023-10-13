plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    signingConfigs {
        create("release") {
            keyAlias = "dbgikey"
            keyPassword = "dbgi_dbgi"
            storePassword = "dbgi_dbgi"
            storeFile =
                file("C:\\Users\\edoua\\Desktop\\DBGI_project\\DBGI_tracking_android\\dbgikey.kts")
        }
    }
    namespace = "org.example.dbgitracking"
    compileSdk = 33

    defaultConfig {
        applicationId = "org.example.dbgitracking"
        minSdk = 29
        //noinspection OldTargetApi
        targetSdk = 33
        versionCode = 1
        versionName = "0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.gms:play-services-vision:20.1.3")
    implementation("org.javatuples:javatuples:1.2")
    implementation("androidx.activity:activity:1.6.1")
    implementation("androidx.fragment:fragment:1.5.5")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.android.support:support-annotations:28.0.0")
    implementation("com.bradyid:BradySdk:1.4.4")
    implementation("com.google.code.gson:gson:2.8.9")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}