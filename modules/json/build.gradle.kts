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

    implementation(project(":modules:api"))

    implementation("com.fasterxml.jackson.core:jackson-databind:${versions["JACKSON"]!!}")
    implementation("com.fasterxml.jackson.core:jackson-core:${versions["JACKSON"]!!}")
    implementation("com.fasterxml.jackson.module:jackson-module-scala_3:${versions["JACKSON_SCALA"]!!}")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
    testImplementation("co.helmethair:scalatest-junit-runner:${versions["SCALATEST_JUNIT"]!!}")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.matching { !it.name.startsWith("scoverage") }.configureEach {
    resolutionStrategy.force("org.scala-lang:scala-library:${versions["SCALA_LIBRARY"]!!}")
}
configurations.scoverage {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.scoverage" && requested.name.startsWith("scalac-scoverage-plugin_")) {
            useTarget("${requested.group}:scalac-scoverage-plugin_2.13.16:2.3.0")
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform {
        includeEngines("scalatest")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    finalizedBy(tasks.reportScoverage)
}
tasks.reportScoverage {
    dependsOn(tasks.test)
}
