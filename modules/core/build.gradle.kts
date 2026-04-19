plugins {
    id("scala")
    id("org.scoverage") version "8.1"
}

group = "de.nowchess"
version = "1.0-SNAPSHOT"

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>

repositories {
    mavenCentral()
}

scala {
    scalaVersion = versions["SCALA3"]!!
}

scoverage {
    scoverageVersion.set(versions["SCOVERAGE"]!!)
}

tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-encoding", "UTF-8")
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

    implementation(project(":modules:api"))
    implementation(project(":modules:io"))
    implementation(project(":modules:rule"))
    implementation(project(":modules:bot"))

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
    testImplementation("co.helmethair:scalatest-junit-runner:${versions["SCALATEST_JUNIT"]!!}")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("scalatest")
        testLogging {
            events("skipped", "failed")
        }
    }
    finalizedBy(tasks.reportScoverage)
}
tasks.reportScoverage {
    dependsOn(tasks.test)
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

