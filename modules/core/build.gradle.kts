plugins {
    id("scala")
}

group = "de.nowchess"
version = "1.0-SNAPSHOT"

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>

repositories {
    mavenCentral()
}

scala {
    versions["SCALA3"]!!
}

dependencies {

    implementation("org.scala-lang:scala3-compiler_3") {
        version {
            strictly(versions["SCALA3"]!!)
        }
    }
    implementation("org.scala-lang:scala3-library_3") {
        version {
            strictly(versions["SCALA3"]!!)
        }
    }
    implementation("org.scala-lang:scala-library") {
        version {
            strictly(versions["SCALA_LIBRARY"]!!)
        }
    }

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}