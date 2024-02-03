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
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain

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
    val settings = getSettings<Settings>(session, user)
    val gmailSvc: Gmail = GmailService.getGmailService()
    val userSvc: Gmail.Users = gmailSvc.users()
    val symbols = mapOf(
      "gmail" to gmailSvc,
      "gmailUser" to "me",
      "gmailUsers" to userSvc,
      "gmailMessages" to userSvc.messages(),
      "gmailLabels" to userSvc.labels(),
    )
    CodingAgent(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = KotlinInterpreter::class,
      symbols = symbols,
      temperature = (settings?.temperature ?: 0.1),
      model = (settings?.model ?: ChatModels.GPT35Turbo),
    ).start(
      userMessage = userMessage,
    )
  }

  data class Settings(
    val region: String? = DefaultAwsRegionProviderChain().getRegion().id(),
    val profile: String? = "default",
    val temperature: Double? = 0.1,
    val model: ChatModels = ChatModels.GPT4Turbo,
  )

  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

}