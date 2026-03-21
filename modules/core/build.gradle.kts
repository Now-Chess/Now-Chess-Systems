plugins {
    id("scala")
    jacoco
    application
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

application {
    mainClass.set("de.nowchess.chess.chessMain")
}

tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-encoding", "UTF-8")
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    standardInput = System.`in`
}

tasks.withType<Test> {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
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

    implementation(project(":modules:api"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}