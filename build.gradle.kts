plugins {
    id("org.sonarqube") version "7.2.3.7755"
    id("org.scoverage") version "8.1" apply false
    id("com.diffplug.spotless") version "8.4.0" apply false
    id("io.github.cosmicsilence.scalafix") version "0.2.6" apply false
}

group = "de.nowchess"
version = "1.0-SNAPSHOT"

sonar {
    properties {
        property("sonar.projectKey", "Now-Chess-Systems")
        property("sonar.projectName", "Now-Chess Systems")
        property("sonar.host.url", "https://sonar.janis-eccarius.de")
        property("sonar.token", System.getenv("SONAR_TOKEN"))
        property("sonar.branch.name", System.getenv("GIT_BRANCH") ?: "main")

        val scoverageReports = subprojects.mapNotNull { subproject ->
            val report = subproject.file("build/reports/scoverageTest/scoverage.xml")
            if (report.exists()) report.absolutePath else null
        }.joinToString(",")

        property("sonar.scala.coverage.reportPaths", scoverageReports)
    }
}

val versions = mapOf(
    "QUARKUS_SCALA3"        to "1.0.0",
    "SCALA3"                to "3.5.1",
    "SCALA_LIBRARY"         to "2.13.18",
    "SCALATEST"             to "3.2.19",
    "SCALATEST_JUNIT"       to "0.1.11",
    "SCOVERAGE"             to "2.1.1",
    "SCALAFX"               to "21.0.0-R32",
    "JAVAFX"                to "21.0.1",
    "JUNIT_BOM"             to "5.13.4",
    "SCALA_PARSER_COMBINATORS" to "2.4.0",
    "FASTPARSE"             to "3.0.2",
    "JACKSON"               to "2.17.2",
    "JACKSON_SCALA"         to "2.17.2"
)
extra["VERSIONS"] = versions

subprojects {
    apply(plugin = "com.diffplug.spotless")

    pluginManager.withPlugin("scala") {
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            scala {
                scalafmt().configFile(rootProject.file(".scalafmt.conf"))
            }
        }

        apply(plugin = "io.github.cosmicsilence.scalafix")
        configure<io.github.cosmicsilence.scalafix.ScalafixExtension> {
            configFile.set(rootProject.file(".scalafix.conf"))
        }

        // Disable SemanticDB config for the scoverage source set — it sets -sourceroot to
        // the root project dir, which conflicts with scoverage's own -sourceroot and causes
        // reportTestScoverage to fail with "No source root found".
        tasks.matching { it.name in setOf("configSemanticDBScoverage", "checkScalafixScoverage", "checkScalafixTest") }.configureEach {
            enabled = false
        }
    }
}

