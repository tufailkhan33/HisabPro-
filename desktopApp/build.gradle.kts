import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqlite.jdbc)
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)
    implementation(libs.org.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/firebaseResources"))
        }
    }
}

val syncFirebaseConfig by tasks.registering(Copy::class) {
    val outDir = layout.buildDirectory.dir("generated/firebaseResources")
    from(rootProject.file("app/google-services.json"))
    from(rootProject.file("app/src/main/res/values/firebase_applet_config.xml"))
    into(outDir)
}

tasks.named("processResources") {
    dependsOn(syncFirebaseConfig)
}

compose.desktop {
    application {
        mainClass = "com.example.desktop.MainKt"
        jvmArgs += listOf("-Xmx1024m", "-Dapple.awt.application.name=HisabPro")

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "HisabPro"
            packageVersion = "1.3.0"
            description = "HisabPro Business Credit, Recovery & Supplier Ledger"
            copyright = "Copyright (c) 2026 HisabPro"
            vendor = "HisabPro"

            includeAllModules = true

            windows {
                iconFile.set(project.file("src/main/resources/hisabpro.ico"))
                dirChooser = true
                menuGroup = "HisabPro"
                shortcut = true
                menu = true
                perUserInstall = false
                upgradeUuid = "9B7E4A31-6D2C-4F18-A9C4-2E8B5F11D403"
            }
        }
    }
}

tasks.withType<Test> {
    failFast = true
    timeout.set(Duration.ofMinutes(2))
    testLogging {
        events("passed", "failed", "skipped")
    }
}
