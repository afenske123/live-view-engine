plugins {
    id("java")
    id("application")
}

group = "de.fenske.liveview"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("de.fenske.liveview.Main")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

// Makes sure the web/ folder (index.html, script.js) is served relative to the
// working directory when the app is run with `./gradlew run`.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
