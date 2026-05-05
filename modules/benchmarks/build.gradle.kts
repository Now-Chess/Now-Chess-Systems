plugins {
    id("scala")
    id("me.champeau.jmh")
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

dependencies {
    implementation("org.scala-lang:scala3-library_3") {
        version {
            strictly(versions["SCALA3"]!!)
        }
    }
    compileOnly("org.scala-lang:scala3-compiler_3") {
        version {
            strictly(versions["SCALA3"]!!)
        }
    }

    // Core modules for benchmarking
    implementation(project(":modules:api"))
    implementation(project(":modules:core"))
    implementation(project(":modules:rule"))
    implementation(project(":modules:bot-platform"))

    // JMH (jmh configuration is for annotation processor + runtime)
    implementation("org.openjdk.jmh:jmh-core:${versions["JMH"]!!}")
    jmh("org.openjdk.jmh:jmh-core:${versions["JMH"]!!}")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:${versions["JMH"]!!}")
}

configurations.matching { !it.name.startsWith("jmh") }.configureEach {
    resolutionStrategy.force("org.scala-lang:scala-library:${versions["SCALA_LIBRARY"]!!}")
}

jmh {
    jmhVersion.set(versions["JMH"]!!)
    includeTests.set(false)
    duplicateClassesStrategy.set(DuplicatesStrategy.WARN)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<org.gradle.api.tasks.scala.ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-encoding", "UTF-8")
}
