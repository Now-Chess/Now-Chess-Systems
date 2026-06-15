// Standalone Spark batch-analytics module.
//
// Spark 3.5.x ships only Scala 2.12/2.13 artifacts; Scala 3 code can consume
// them via JVM binary compatibility so long as we avoid macro-expanded APIs
// (spark.implicits._, typed Dataset[T]).  We use the untyped DataFrame API
// exclusively, which is safe to call from Scala 3.
//
// Spark is declared compileOnly — the cluster provides it at runtime via
// spark-submit.  Only the PostgreSQL driver and the Scala 3 runtime are
// bundled into the fat jar produced by the "jar" task.
//
// Build the submission jar:
//   ./gradlew :modules:analytics:jar
//
// Run a job:
//   spark-submit \
//     --class de.nowchess.analytics.OpeningBookJob \
//     modules/analytics/build/libs/analytics-<version>.jar \
//     [outputDir] [maxPlies]
//
// Environment variables consumed:
//   NOWCHESS_JDBC_URL  (default: jdbc:postgresql://localhost:5432/nowchess)
//   NOWCHESS_DB_USER   (default: nowchess)
//   NOWCHESS_DB_PASS   (default: nowchess)

plugins {
    id("scala")
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

val sparkVersion = "3.5.4"

dependencies {
    compileOnly("org.scala-lang:scala3-compiler_3") {
        version { strictly(versions["SCALA3"]!!) }
    }
    implementation("org.scala-lang:scala3-library_3") {
        version { strictly(versions["SCALA3"]!!) }
    }
    implementation("org.scala-lang:scala-library") {
        version { strictly(versions["SCALA_LIBRARY"]!!) }
    }

    // Spark is provided by the cluster — compile-only, not bundled.
    compileOnly("org.apache.spark:spark-sql_2.13:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    compileOnly("org.apache.spark:spark-core_2.13:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    // PostgreSQL JDBC driver bundled so it is available on executor classpath.
    implementation("org.postgresql:postgresql:42.7.4")
}

application {
    mainClass.set("de.nowchess.analytics.OpeningBookJob")
}

// Fat jar: includes runtimeClasspath (our code + pg driver + scala3-library)
// but NOT compileOnly Spark jars.
tasks.jar {
    manifest {
        attributes["Main-Class"] = "de.nowchess.analytics.OpeningBookJob"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-encoding", "UTF-8")
}
