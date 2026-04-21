plugins {
    id("java")
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
}

tasks.test {
    useJUnitPlatform()
}