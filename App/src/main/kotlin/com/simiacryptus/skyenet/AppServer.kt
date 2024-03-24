package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.apps.coding.*
import com.simiacryptus.skyenet.apps.general.WebDevApp
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationDirectory
import com.simiacryptus.skyenet.webui.servlet.InterpreterAndTools
import com.simiacryptus.skyenet.webui.servlet.OAuthGoogle
import com.simiacryptus.skyenet.webui.servlet.ToolServlet


open class AppServer(
  localName: String, publicName: String, port: Int
) : ApplicationDirectory(
  localName = localName, publicName = publicName, port = port
) {

  override val toolServlet = object : ToolServlet(this@AppServer) {
    override fun fromString(user: User, str: String): InterpreterAndTools {
      val parts = str.split(":", limit = 2)
      return when (Class.forName(parts[0]) as Class) {
        AwsCodingApp::class.java -> AwsCodingApp.fromString(if(parts.size > 1) parts[1] else "")
        GmailCodingApp::class.java -> GmailCodingApp.fromString(user, if(parts.size > 1) parts[1] else "")
        JDBCCodingApp::class.java -> JDBCCodingApp.fromString(user, if(parts.size > 1) parts[1] else "")
        PowershellCodingApp::class.java -> PowershellCodingApp.fromString(user, if(parts.size > 1) parts[1] else "")
        BashCodingApp::class.java -> BashCodingApp.fromString(user, if(parts.size > 1) parts[1] else "")
        SeleniumCodingApp::class.java -> SeleniumCodingApp.fromString(user, if(parts.size > 1) parts[1] else "")
        else -> throw IllegalArgumentException(parts[0])
      }
    }
  }

   override val childWebApps by lazy {
    listOf(
      ChildWebApp("/aws", AwsCodingApp()),
      ChildWebApp("/gmail", GmailCodingApp()),
      ChildWebApp("/selenium", SeleniumCodingApp()),
      ChildWebApp("/bash", BashCodingApp()),
      ChildWebApp("/powershell", PowershellCodingApp()),
      ChildWebApp("/webdev", WebDevApp()),
      ChildWebApp("/jdbc", JDBCCodingApp()),
      ChildWebApp("/taskDev", TaskRunnerApp()),
    )
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      OAuthGoogle.scopes += listOf(
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile",
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/gmail.modify",
        "https://www.googleapis.com/auth/gmail.compose",
        "https://www.googleapis.com/auth/gmail.send",
        "https://www.googleapis.com/auth/gmail.insert",
        "https://www.googleapis.com/auth/gmail.labels",
        "https://www.googleapis.com/auth/gmail.metadata",
        "https://www.googleapis.com/auth/gmail.settings.basic",
        "https://www.googleapis.com/auth/gmail.settings.sharing",
        "https://www.googleapis.com/auth/gmail.addons.current.message.action",
        "https://www.googleapis.com/auth/gmail.addons.current.message.metadata",
        "https://www.googleapis.com/auth/gmail.addons.current.message.readonly",
        "https://www.googleapis.com/auth/gmail.addons.current.action.compose",
        "https://mail.google.com/",
        "https://www.googleapis.com/auth/drive",
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/drive.appdata",
        "https://www.googleapis.com/auth/drive.metadata.readonly",
        "https://www.googleapis.com/auth/calendar",
        "https://www.googleapis.com/auth/calendar.events",
        "https://www.googleapis.com/auth/calendar.events.readonly",
        "https://www.googleapis.com/auth/calendar.readonly",
      )
      AppServer(localName = "localhost", "localhost", 37600)._main(args)
    }
  }

}



