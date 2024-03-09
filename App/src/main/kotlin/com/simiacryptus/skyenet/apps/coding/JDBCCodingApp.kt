package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.servlet.InterpreterAndTools
import java.sql.Connection
import java.sql.DriverManager
import java.util.function.Consumer
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class JDBCCodingApp : ApplicationServer(
  applicationName = "JDBC Coding Assistant v1.0",
  path = "/jdbc",
) {
  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val settings = getSettings(session, user) ?: Settings()
    val jdbcConnection: Connection = DriverManager.getConnection(
      settings.jdbcUrl,
      settings.jdbcUser,
      settings.jdbcPassword
    )
    object : ToolAgent<KotlinInterpreter>(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = KotlinInterpreter::class,
      symbols = getSymbols(jdbcConnection),
      temperature = (settings.temperature ?: 0.1),
      model = settings.model,
    ) {
      override fun getInterpreterString(): String {
        return JDBCCodingApp::class.java.name + ":${JsonUtil.toJson(settings)}"
      }

    }.start(
      userMessage = userMessage,
    )
  }

  fun getSymbols(jdbcConnection: Connection) = mapOf(
    "withConnection" to JDBCSupplier(jdbcConnection),
  )

  class JDBCSupplier(private val jdbcConnection: Connection) : Consumer<(Connection) -> Unit> {
    override fun accept(function: (Connection) -> Unit) {
      function(jdbcConnection)
    }
  }

  data class Settings(
    val temperature: Double? = 0.1,
    val model: OpenAITextModel = ChatModels.GPT4Turbo,
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/postgres",
    val jdbcUser: String = "postgres",
    val jdbcPassword: String = "password",
    val jdbcDriver: String = "org.postgresql.Driver"
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T = Settings() as T

  companion object {
    @OptIn(ExperimentalStdlibApi::class)
    fun fromString(user: User, string: String) = JsonUtil.fromJson<Settings>(string, typeOf<Settings>().javaType).let { settings ->
      InterpreterAndTools(
          KotlinInterpreter::class.java,
          JDBCCodingApp().getSymbols(
            DriverManager.getConnection(
              settings.jdbcUrl,
              settings.jdbcUser,
              settings.jdbcPassword
            )
          )
        )
      }
  }

}