plugins {
    id("java")
    id("application")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.apache.xmlbeans:xmlbeans:5.3.0")
    implementation("org.xerial:sqlite-jdbc:3.53.0.0")

    implementation(files("libs/xmlbeans-message.jar"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Runs TCP server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.example.Server")
}

tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Runs TCP client"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.example.Client")
    standardInput = System.`in`
}