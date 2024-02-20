package com.simiacryptus.skyenet.apps.coding

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.apache.v2.GoogleApacheHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.drive.Drive
import com.google.api.services.gmail.Gmail
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.apps.coding.GmailCodingApp.GmailSupplier.CalendarSupplier
import com.simiacryptus.skyenet.apps.coding.GmailCodingApp.GmailSupplier.DriveSupplier
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.servlet.InterpreterAndTools
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
    val settings = getSettings(session, user) ?: Settings()
    val gmailSvc: Gmail = Gmail
      .Builder(
        GoogleApacheHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
        user?.credential as Credential
      )
      .setApplicationName(applicationName)
      .build()
    val driveSvc: Drive = Drive.Builder(
      GoogleApacheHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), user?.credential as Credential
    )
      .setApplicationName(applicationName)
      .build()
    val calendarSvc: Calendar = Calendar.Builder(
      GoogleApacheHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), user?.credential as Credential
    )
      .setApplicationName(applicationName)
      .build()
    object : ToolAgent<KotlinInterpreter>(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = KotlinInterpreter::class,
      symbols = getSymbols(gmailSvc, driveSvc, calendarSvc),
      temperature = (settings?.temperature ?: 0.1),
      model = (settings?.model ?: ChatModels.GPT35Turbo),
    ) {
      override fun getInterpreterString(): String = GmailCodingApp::class.java.name

    }.start(
      userMessage = userMessage,
    )
  }

  fun getSymbols(gmailSvc: Gmail, driveSvc: Drive, calendarSvc: Calendar) = mapOf(
    "gmail" to GmailSupplier(gmailSvc),
    "drive" to DriveSupplier(driveSvc),
    "calendar" to CalendarSupplier(calendarSvc),
  )

  class GmailSupplier(private val gmailSvc: Gmail) : Supplier<Gmail> {
    override fun get(): Gmail {
      return gmailSvc
    }

    class DriveSupplier(private val driveSvc: Drive) : Supplier<Drive> {
      override fun get(): Drive {
        return driveSvc
      }
    }

    class CalendarSupplier(private val calendarSvc: Calendar) : Supplier<Calendar> {
      override fun get(): Calendar {
        return calendarSvc
      }
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
    fun fromString(user: User, string: String): InterpreterAndTools {
      val transport = GoogleApacheHttpTransport.newTrustedTransport()
      val gsonFactory = GsonFactory.getDefaultInstance()
      return InterpreterAndTools(
        KotlinInterpreter::class.java,
        GmailCodingApp().getSymbols(
          Gmail
            .Builder(
              transport,
              gsonFactory,
              user.credential as Credential
            )
            .setApplicationName("GMail Coding Assistant v1.0")
            .build(),
          Drive
            .Builder(
              transport,
              gsonFactory,
              user.credential as Credential
            )
            .setApplicationName("GMail Coding Assistant v1.0")
            .build(),
          Calendar
            .Builder(
              transport,
              gsonFactory,
              user.credential as Credential
            )
            .setApplicationName("GMail Coding Assistant v1.0")
            .build()
        ),
      )
    }
  }

}



