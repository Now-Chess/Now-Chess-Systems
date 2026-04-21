plugins {
    id("scala")
    id("org.scoverage") version "8.1"
}

group = "de.nowchess"
version = "1.0-SNAPSHOT"

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
val scoverageExcluded = rootProject.extra["SCOVERAGE_EXCLUDED"] as List<String>

repositories {
    mavenCentral()
}

scala {
    scalaVersion = versions["SCALA3"]!!
}

scoverage {
    scoverageVersion.set(versions["SCOVERAGE"]!!)
    excludedFiles.set(scoverageExcluded)
}

configurations.scoverage {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.scoverage" && requested.name.startsWith("scalac-scoverage-plugin_")) {
            useTarget("${requested.group}:scalac-scoverage-plugin_2.13.16:2.3.0")
        }
    }
}

dependencies {

    compileOnly("org.scala-lang:scala3-compiler_3") {
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