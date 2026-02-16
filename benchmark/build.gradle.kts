plugins {
    java
}

group = "org.iumotionlabs"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

fun brotli4jPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") && arch.contains("aarch64") -> "osx-aarch64"
        os.contains("mac") -> "osx-x86_64"
        os.contains("linux") && arch.contains("aarch64") -> "linux-aarch64"
        os.contains("linux") -> "linux-x86_64"
        os.contains("win") -> "windows-x86_64"
        else -> "linux-x86_64"
    }
}

dependencies {
    implementation(fileTree("../java/build/libs") { include("ironcompress*.jar") })

    // Competitor libraries
    implementation("at.yawk.lz4:lz4-java:1.10.3")
    implementation("org.xerial.snappy:snappy-java:1.1.10.8")
    implementation("com.github.luben:zstd-jni:1.5.7-7")
    implementation("com.aayushatharva.brotli4j:brotli4j:1.20.0")
    runtimeOnly("com.aayushatharva.brotli4j:native-${brotli4jPlatform()}:1.20.0")
    implementation("org.tukaani:xz:1.11")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.ning:compress-lzf:1.2.0")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.3")
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

tasks.register<JavaExec>("compare") {
    mainClass.set("io.ironcompress.benchmark.report.ComparisonRunner")
    classpath = sourceSets["main"].runtimeClasspath

    val nativeLibPath = project.findProperty("native.lib.path")?.toString()
    if (nativeLibPath != null) {
        systemProperty("native.lib.path", nativeLibPath)
        jvmArgs("-Djava.library.path=$nativeLibPath")
    }

    // benchmark.size: generate data of this size (e.g. "1GB", "500MB", "10MB")
    project.findProperty("benchmark.size")?.toString()?.let {
        systemProperty("benchmark.size", it)
    }

    // benchmark.file: path to an external file to benchmark
    project.findProperty("benchmark.file")?.toString()?.let {
        systemProperty("benchmark.file", it)
    }

    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgs("-Xmx4g")
}
