package com.simiacryptus.skyenet.apps.coding

import com.google.api.services.gmail.Gmail
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.GmailService
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.application.InterpreterAndTools
import com.simiacryptus.skyenet.webui.application.ToolAgent
import java.util.function.Supplier

class GmailCodingApp : ApplicationServer(
  applicationName = "GMail Coding Assistant v1.0",
  path = "/gmail",
) {
  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val settings = getSettings<Settings>(session, user) ?: Settings()
    val gmailSvc: Gmail = GmailService.getGmailService()
    object : ToolAgent<KotlinInterpreter>(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = KotlinInterpreter::class,
      symbols = getSymbols(gmailSvc),
      temperature = (settings?.temperature ?: 0.1),
      model = (settings?.model ?: ChatModels.GPT35Turbo),
    ) {
      override fun getInterpreterString(): String = GmailCodingApp::class.java.name

    }.start(
      userMessage = userMessage,
    )
  }

  fun getSymbols(gmailSvc: Gmail) = mapOf(
    "gmail" to GmailSupplier(gmailSvc),
  )

  class GmailSupplier(private val gmailSvc: Gmail) : Supplier<Gmail> {
    override fun get(): Gmail {
      return gmailSvc
    }
  }

  data class Settings(
    val temperature: Double? = 0.1,
    val model: ChatModels = ChatModels.GPT4Turbo,
  )

  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

  companion object {
    fun fromString(string: String): InterpreterAndTools {
      val gmailSvc: Gmail = GmailService.getGmailService()
      return InterpreterAndTools(
        KotlinInterpreter::class.java,
        GmailCodingApp().getSymbols(gmailSvc),
      )
    }
  }

}

