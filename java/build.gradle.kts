plugins {
    java
    `maven-publish`
}

version = project.findProperty("releaseVersion")?.toString() ?: "0.1.0"
group = "org.iumotionlabs"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

dependencies {
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("IronCompress")
                description.set("Multi-algorithm compression for Java, powered by Rust via FFM")
                url.set("https://github.com/cburgosro9303/ironcompress")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cburgosro9303/ironcompress")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
