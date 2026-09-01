plugins {
    kotlin("jvm") version "2.3.10"
    id("dev.botta.kotlin-conventions") version "0.4.2"
    application
}

group = "lab.fanout"
version = "0.1.0"

val trantorVersion = "0.8.1-beta11"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("dev.botta.trantor:trantor-bom:$trantorVersion"))
    implementation("dev.botta.trantor:trantor-web")
    testImplementation("dev.botta.trantor:trantor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
    }
}

application {
    mainClass.set("lab.fanout.MainKt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
