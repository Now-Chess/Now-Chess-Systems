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

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    compileOnly("org.scala-lang:scala3-compiler_3") {
        version { strictly(versions["SCALA3"]!!) }
    }
    implementation("org.scala-lang:scala3-library_3") {
        version { strictly(versions["SCALA3"]!!) }
    }

    compileOnly(platform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    compileOnly("io.quarkus:quarkus-rest")
    compileOnly("io.quarkus:quarkus-rest-client")
    compileOnly("io.quarkus:quarkus-grpc")
    compileOnly("io.quarkus:quarkus-arc")
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
    options.compilerArgs.add("-parameters")
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
