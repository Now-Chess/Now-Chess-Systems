plugins {
    id("scala")
    id("org.scoverage") version "8.1"
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

    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
    testImplementation("org.scalatestplus:junit-5-11_3:${versions["SCALATESTPLUS_JUNIT5"]!!}")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.reportScoverage)
}
tasks.reportScoverage {
    dependsOn(tasks.test)
}
