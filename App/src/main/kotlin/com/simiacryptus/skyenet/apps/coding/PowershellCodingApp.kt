package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.interpreter.ProcessInterpreter
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.servlet.InterpreterAndTools
import java.io.File

class PowershellCodingApp(
) : ApplicationServer(
  applicationName = "Powershell Coding Assistant v1.0",
  path = "/powershell",
) {

  data class Settings(
    val env: Map<String, String> = mapOf(),
    val workingDir: String = ".",
    val model: OpenAITextModel = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
    val language: String = "powershell",
    val command: List<String> = listOf("powershell"),
  )

  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val settings = getSettings<Settings>(session, user)
    object : ShellToolAgent<ProcessInterpreter>(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = ProcessInterpreter::class,
      symbols = symbols(settings),
      temperature = (settings?.temperature ?: 0.1),
      model = (settings?.model ?: ChatModels.GPT35Turbo),
    ) {
      override fun getInterpreterString(): String {
        return this@PowershellCodingApp::class.java.canonicalName
      }

    }.start(
      userMessage = userMessage,
    )
  }

  private fun symbols(settings: Settings?) = mapOf(
    "env" to (settings?.env ?: mapOf()),
    "workingDir" to File(settings?.workingDir ?: ".").absolutePath,
    "language" to (settings?.language ?: "powershell"),
    "command" to (settings?.command ?: listOf("powershell")),
  )

  companion object {
    fun fromString(user: User, params: String): InterpreterAndTools {
      return InterpreterAndTools(
        interpreterClass = KotlinInterpreter::class.java,
        symbols = mapOf(
          "env" to mapOf<String, String>(
          ),
        ),
      )
    }
  }
}