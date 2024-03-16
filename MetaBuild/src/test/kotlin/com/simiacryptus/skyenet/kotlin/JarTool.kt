@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.simiacryptus.skyenet.kotlin

import com.simiacryptus.skyenet.core.util.ClasspathRelationships.classToPath
import com.simiacryptus.skyenet.core.util.ClasspathRelationships.downstreamMap
import com.simiacryptus.skyenet.core.util.ClasspathRelationships.readJarClasses
import com.simiacryptus.skyenet.core.util.ClasspathRelationships.readJarFiles
import com.simiacryptus.skyenet.core.util.RuleTreeBuilder.getRuleExpression
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.*

object JarTool {
  val buildFile = File("""C:\Users\andre\code\SkyenetApps\App\build.gradle.kts""")
  val classloadLog: String? = null
  val pluginJar = """C:\Users\andre\code\SkyenetApps\App\App-1.0.0-full.jar"""

  val requiredRoots = listOf(
    "com.simiacryptus.skyenet.AppServer",
    "kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactoryKt",
    "kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223ScriptEngineImpl",
    "org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineBase",
    "kotlin.script.experimental.jvmhost.BasicJvmScriptingHostKt",
    "org.jetbrains.kotlin.jsr223.KotlinJsr223JvmScriptEngine4Idea",
  )

  val requiredResources: List<String> by lazy {
    readJarFiles(pluginJar).filter {
      when {
        it.endsWith(".class") -> false
        it.startsWith("META-INF/services/") -> true
        it.startsWith("META-INF/extensions/compiler.xml") -> true
        else -> false
      }
    }
  }

  private fun isPrivate(to: String) =
    (pluginAccessMap[to] ?: throw IllegalStateException(to)).and(Opcodes.ACC_PRIVATE) != 0

  private fun isPublic(to: String) =
    (pluginAccessMap[to] ?: throw IllegalStateException(to)).and(Opcodes.ACC_PUBLIC) != 0

  val pluginJarClasses by lazy { readJarClasses(pluginJar) }
  val pluginClasspath by lazy {
    analyzeJar(pluginJarClasses)
      .filter {
        when {
          !pluginJarClasses.containsKey(it.to) -> false
          !pluginJarClasses.containsKey(it.from) -> false
          else -> true
        }
      }
      .apply { require(isNotEmpty()) }.groupBy { it.from }
  }

  val pluginAccessMap by lazy {
    classAccessMap(pluginJarClasses).entries.map {
      it.key to it.value
    }.toMap()
  }

  val classloadLogClasses by lazy {
    classloadLog?.let {
      File(classloadLog).readLines().filter { it.contains("class,load") }.map {
        it.substringAfter("] ").substringBefore(" ")
      }.toSortedSet()
    } ?: sortedSetOf()
  }

  val requiredClasses: SortedSet<String> by lazy {
    val requirementMap = downstreamMap(pluginClasspath.values.flatten())
    (((requiredRoots).distinct().flatMap {
      downstream(requirementMap, it, mutableSetOf(it))
    } + classloadLogClasses).toSet()).toSortedSet()
  }

  val deadWeight by lazy {
    pluginClasspath.keys
      .filter { !requiredClasses.contains(it) }
      .filter { !classloadLogClasses.contains(it) }
      .toSortedSet()
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val code = """      
      // GENERATED CODE

      // Pruned: ${deadWeight.size}
      // Required Classes: ${requiredClasses.size}
      
      // Pruned:
      fun isPruned(path: String) = ${
      getRuleExpression(
        (deadWeight).map { it.classToPath + ".class" }.toSet(),
        (pluginClasspath.keys
          .filter { !deadWeight.contains(it) }
          .map { it.classToPath + ".class" }).toSortedSet(),
        true
      )
    }    
      """.trimIndent()

    val text = buildFile.readText()
    val start = text.indexOf("// GENERATED CODE")
    buildFile.writeText(text.substring(0, start) + code)
    println(code)
  }

}
