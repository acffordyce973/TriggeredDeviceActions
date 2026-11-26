import java.io.File
import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.kotlin.serialization)
}

val keystoreProperties = Properties()
val fileKeystoreProps: File? = when {
	rootProject.file("keystore.properties").exists() -> rootProject.file("keystore.properties")
	file("keystore.properties").exists() -> file("keystore.properties")
	else -> null
}
if (fileKeystoreProps != null && fileKeystoreProps.exists()) {
	fileKeystoreProps.inputStream().use { keystoreProperties.load(it) }
}

android {
	namespace = "com.antigravity.triggeredactions"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.antigravity.triggeredactions"
		minSdk = 29
		targetSdk = 34
		versionCode = 100
		versionName = "1.0.0"
	}

	signingConfigs {
		create("release") {
			if (keystoreProperties.containsKey("storeFile")) {
				val stringStoreFile = keystoreProperties.getProperty("storeFile")
				val fileStore = when {
					rootProject.file(stringStoreFile).exists() -> rootProject.file(stringStoreFile)
					else -> file(stringStoreFile)
				}
				storeFile = fileStore
				storePassword = keystoreProperties.getProperty("storePassword")
				keyAlias = keystoreProperties.getProperty("keyAlias")
				keyPassword = keystoreProperties.getProperty("keyPassword")
			} else {
				val objDebugSigning = signingConfigs.getByName("debug")
				storeFile = objDebugSigning.storeFile
				storePassword = objDebugSigning.storePassword
				keyAlias = objDebugSigning.keyAlias
				keyPassword = objDebugSigning.keyPassword
			}
		}
	}

	buildTypes {
		debug {
			signingConfig = signingConfigs.getByName("release")
		}
		release {
			isMinifyEnabled = false
			signingConfig = signingConfigs.getByName("release")
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	lint {
		disable += "InvalidFragmentVersionForActivityResult"
		disable += "ExpiredTargetSdkVersion"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		compose = true
		aidl = false
		buildConfig = false
		shaders = false
	}

	testOptions {
		unitTests.isReturnDefaultValues = true
	}

	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

kotlin {
	jvmToolchain(17)
}

dependencies {
	val objComposeBom = platform(libs.androidx.compose.bom)
	implementation(objComposeBom)
	androidTestImplementation(objComposeBom)

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)

	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.material.icons.extended)
	debugImplementation(libs.androidx.compose.ui.tooling)
	debugImplementation(libs.androidx.compose.ui.test.manifest)

	implementation(libs.androidx.documentfile)
	implementation(libs.play.services.location)
	implementation(libs.androidx.work.runtime.ktx)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.okhttp)
	implementation(libs.androidx.webkit)

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.okhttp.mockwebserver)

	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.test.core)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.espresso.core)
}
