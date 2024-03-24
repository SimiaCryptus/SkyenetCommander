import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  `java-library`
  `maven-publish`
  id("signing")
  id("org.jetbrains.kotlin.jvm") version "1.9.21"
}

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

repositories {
  mavenCentral {
    metadataSources {
      mavenPom()
      artifact()
    }
  }
  maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies")
}

kotlin {
  jvmToolchain(17)
}

val skyenet_version = "1.0.49"
dependencies {
  implementation(kotlin("stdlib-jdk8"))
  testImplementation(group = "com.simiacryptus.skyenet", name = "core", version = skyenet_version)
  testImplementation(group = "com.simiacryptus.skyenet", name = "kotlin", version = skyenet_version)

  testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.10.1")
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.10.1")
  testImplementation(group = "ch.qos.logback", name = "logback-classic", version = "1.4.11")
  testImplementation(group = "ch.qos.logback", name = "logback-core", version = "1.4.11")
  testImplementation("org.ow2.asm:asm:9.6")
  testImplementation("org.jetbrains.kotlin", name = "kotlin-script-runtime", version = "1.9.21")
}


tasks {

  compileKotlin {
    compilerOptions {
      javaParameters = true
    }
  }
  compileTestKotlin {
    compilerOptions {
      javaParameters = true
    }
  }
  test {
    useJUnitPlatform()
    systemProperty("surefire.useManifestOnlyJar", "false")
    testLogging {
      events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
      exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    jvmArgs(
      "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens", "java.base/java.util=ALL-UNNAMED",
      "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
  }
}

