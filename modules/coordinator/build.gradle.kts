plugins {
    id("scala")
    id("org.scoverage") version "8.1"
    id("io.quarkus")
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

tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-encoding", "UTF-8")
}

tasks.named("compileScoverageJava").configure {
    dependsOn(tasks.named("quarkusGenerateCode"))
}

tasks.withType(ScalaCompile::class).configureEach {
    if (name == "compileScoverageScala") {
        source = source.asFileTree.matching {
            exclude("**/grpc/*.scala")
            exclude("**/service/*.scala")
            exclude("**/resource/*.scala")
            exclude("**/config/*.scala")
        }
    }
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

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

    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-rest-client")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("com.fasterxml.jackson.module:jackson-module-scala_3:${versions["JACKSON_SCALA"]!!}")
    implementation("io.quarkus:quarkus-redis-client")
    implementation("io.fabric8:kubernetes-client:6.13.0")

    testImplementation(platform("org.junit:junit-bom:${versions["JUNIT_BOM"]!!}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
    testImplementation("co.helmethair:scalatest-junit-runner:${versions["SCALATEST_JUNIT"]!!}")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
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
tasks.withType<Jar>().configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }

tasks.test {
    useJUnitPlatform {
        includeEngines("scalatest", "junit-jupiter")
        testLogging { events("passed", "skipped", "failed") }
    }
    finalizedBy(tasks.reportScoverage)
}
tasks.reportScoverage { dependsOn(tasks.test) }
tasks.jar { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
