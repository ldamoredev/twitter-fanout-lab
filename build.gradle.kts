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

val withNode = layout.projectDirectory.file("scripts/with-node.sh").asFile

val panelNpmCi = tasks.register<Exec>("panelNpmCi") {
    group = "build"
    workingDir = file("panel")
    commandLine(withNode, "npm", "ci")
    inputs.file("panel/package-lock.json")
    outputs.dir("panel/node_modules")
}

val panelTest = tasks.register<Exec>("panelTest") {
    group = "verification"
    dependsOn(panelNpmCi)
    workingDir = file("panel")
    commandLine(withNode, "npm", "test")
    inputs.dir("panel/src")
    inputs.files("panel/package.json", "panel/package-lock.json", "panel/vite.config.ts", "panel/tsconfig.json")
}

val panelBuild = tasks.register<Exec>("panelBuild") {
    group = "build"
    dependsOn(panelNpmCi)
    workingDir = file("panel")
    commandLine(withNode, "npm", "run", "build")
    inputs.dir("panel/src")
    inputs.files(
        "panel/index.html",
        "panel/modelo.html",
        "panel/fanout.html",
        "panel/package.json",
        "panel/package-lock.json",
        "panel/vite.config.ts",
        "panel/tsconfig.json",
    )
    outputs.dir("resources/public")
}

tasks.named("processResources") {
    dependsOn(panelBuild)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        // `./lab bench` lo prende para ver la medición del fan-out; el resto del tiempo molesta.
        showStandardStreams = providers.gradleProperty("labVerbose").isPresent
    }
    dependsOn(panelTest)
}
