package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.AuthenticationInterface
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.servlet.InterpreterAndTools
import com.simiacryptus.skyenet.webui.util.Selenium2S3
import jakarta.servlet.http.Cookie
import org.apache.hc.client5.http.cookie.BasicCookieStore
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie
import org.openqa.selenium.WebDriver
import java.util.concurrent.ThreadFactory

class SeleniumCodingApp : ApplicationServer(
  applicationName = "Selenium Coding Assistant v1.0",
  path = "/selenium",
) {
  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val settings = this@SeleniumCodingApp.getSettings(session, user) ?: Settings()
    object : ToolAgent<KotlinInterpreter>(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = KotlinInterpreter::class,
      symbols = getSymbols(session, user, ui),
      temperature = (settings.temperature ?: 0.1),
      model = settings.model,
    ) {
      override fun getInterpreterString(): String = SeleniumCodingApp::class.java.name
      override fun codeRequest(messages: List<Pair<String, ApiModel.Role>>): CodingActor.CodeRequest {
        return CodingActor.CodeRequest(
          messages = messages,
          codePrefix = imports
        )
      }
    }.start(
      userMessage = userMessage,
    )
  }

  data class Settings(
    val temperature: Double? = 0.1,
    val model: ChatModels = ChatModels.GPT4Turbo,
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

  fun getSymbols(
    session: Session?,
    user: User?,
    ui: ApplicationInterface?
  ): Map<String, Any> {
    val cookies: Array<out Cookie> = listOf(
      Cookie(AuthenticationInterface.AUTH_COOKIE, session?.toString() ?: "").apply {
        domain = "localhost"
      }
    ).filter { it.value.isNotBlank() }.toTypedArray()
    val driver: WebDriver by lazy { Selenium2S3.chromeDriver().apply { Selenium2S3.setCookies(this, cookies) } }
    val threadFactory = session?.let {
      ApplicationServices.clientManager.getPool(it, user, dataStorage).threadFactory
    } ?: ThreadFactory { Thread(it) }
    val httpClient by lazy {
      HttpAsyncClientBuilder.create()
        .useSystemProperties()
        .setDefaultCookieStore(BasicCookieStore().apply {
          cookies.forEach { cookie -> addCookie(BasicClientCookie(cookie.name, cookie.value)) }
        })
        .setThreadFactory(threadFactory)
        .build()
        .also { it.start() }
    }

    val symbols = mapOf(
      "driver" to driver,
      "httpClient" to httpClient,
      "task" to ui?.newTask()
    ).filter { it.value != null }.mapValues { it.value!! }
    return symbols
  }

  companion object {
    fun fromString(user: User, string: String): InterpreterAndTools {
      return InterpreterAndTools(
        KotlinInterpreter::class.java,
        SeleniumCodingApp().getSymbols(null, user, null),
      )
    }

    val imports =
      """
        import com.simiacryptus.jopenai.API
        import com.simiacryptus.jopenai.ApiModel
        import com.simiacryptus.jopenai.models.ChatModels
        import com.simiacryptus.skyenet.core.actors.CodingActor
        import com.simiacryptus.skyenet.core.platform.ApplicationServices
        import com.simiacryptus.skyenet.core.platform.AuthenticationInterface
        import com.simiacryptus.skyenet.core.platform.Session
        import com.simiacryptus.skyenet.core.platform.User
        import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
        import com.simiacryptus.skyenet.webui.application.ApplicationInterface
        import com.simiacryptus.skyenet.webui.application.ApplicationServer
        import com.simiacryptus.skyenet.webui.servlet.InterpreterAndTools
        import com.simiacryptus.skyenet.webui.util.Selenium2S3
        import org.openqa.selenium.chrome.ChromeDriver
        import jakarta.servlet.http.Cookie
        import org.apache.hc.client5.http.cookie.BasicCookieStore
        import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
        import org.apache.hc.client5.http.impl.cookie.BasicClientCookie
        import org.openqa.selenium.WebDriver
        import java.util.concurrent.ThreadFactory
      """.trimIndent()
  }

}