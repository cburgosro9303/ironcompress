plugins {
    java
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()

    val nativeLibPath = project.findProperty("native.lib.path")?.toString()
    if (nativeLibPath != null) {
        systemProperty("native.lib.path", nativeLibPath)
    }

    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
