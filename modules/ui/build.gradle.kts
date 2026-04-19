import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    id("scala")
    id("org.scoverage")
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
    scalaVersion = versions["SCALA3"]!!
}

scoverage {
    scoverageVersion.set(versions["SCOVERAGE"]!!)
    excludedPackages.set(listOf("de\\.nowchess\\.ui\\..*"))
}

application {
    mainClass.set("de.nowchess.ui.Main")
}

tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-encoding", "UTF-8")
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    standardInput = System.`in`
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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

    implementation(project(":modules:core"))
    implementation(project(":modules:rule"))
    implementation(project(":modules:api"))
    implementation(project(":modules:io"))
    implementation(project(":modules:bot"))

    // ScalaFX dependencies
    implementation("org.scalafx:scalafx_3:${versions["SCALAFX"]!!}")
    
    // JavaFX dependencies for the current platform
    val javaFXVersion = versions["JAVAFX"]!!
    val osName = System.getProperty("os.name").lowercase()
    val platform = when {
        osName.contains("win") -> "win"
        osName.contains("mac") -> "mac"
        osName.contains("linux") -> "linux"
        else -> "linux"
    }
    
    listOf("base", "controls", "graphics", "media").forEach { module ->
        implementation("org.openjfx:javafx-$module:$javaFXVersion:$platform")
    }

    testImplementation(platform("org.junit:junit-bom:${versions["JUNIT_BOM"]!!}"))
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
