package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.application.InterpreterAndTools
import com.simiacryptus.skyenet.webui.application.ToolAgent
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain

class AwsCodingApp : ApplicationServer(
  applicationName = "AWS Coding Assistant v1.0",
  path = "/aws",
) {
  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val settings = getSettings<Settings>(session, user)
    val region = settings?.region
    val profile = settings?.profile
    object : ToolAgent<KotlinInterpreter>(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = KotlinInterpreter::class,
      symbols = getSymbols(region, profile),
      temperature = (settings?.temperature ?: 0.1),
      model = (settings?.model ?: ChatModels.GPT35Turbo),
    ) {
      override fun getInterpreterString(): String {
        return AwsCodingApp::class.java.name + ":${settings?.region}, ${settings?.profile})"
      }

    }.start(
      userMessage = userMessage,
    )
  }

  data class Settings(
    val region: String? = DefaultAwsRegionProviderChain().getRegion().id(),
    val profile: String? = "default",
    val temperature: Double? = 0.1,
    val model: ChatModels = ChatModels.GPT35Turbo,
  )

  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

  companion object {
    fun fromString(str: String): InterpreterAndTools {
      val parts = str.split(", ")
      return InterpreterAndTools(
        KotlinInterpreter::class.java,
        getSymbols(parts[0], parts[1])
      )
    }

    fun getSymbols(region: String?, profile: String?) = mapOf(
      "awsRegion" to (region ?: DefaultAwsRegionProviderChain().getRegion().id()),
      "awsProfile" to (profile ?: "default"),
    )
  }

}