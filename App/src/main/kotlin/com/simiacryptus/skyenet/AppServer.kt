package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.apps.coding.*
import com.simiacryptus.skyenet.apps.general.WebDevApp
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationDirectory
import com.simiacryptus.skyenet.webui.servlet.InterpreterAndTools
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
        else -> throw IllegalArgumentException(parts[0])
      }
    }
  }

  //    private val sparkConf = SparkConf().setMaster("local[*]").setAppName("Spark Coding Assistant")
  override val childWebApps by lazy {
    listOf(
      ChildWebApp("/aws", AwsCodingApp()),
      ChildWebApp("/gmail", GmailCodingApp()),
      ChildWebApp("/bash", BashCodingApp()),
      ChildWebApp("/powershell", PowershellCodingApp()),
      ChildWebApp("/webdev", WebDevApp()),
      ChildWebApp("/jdbc", JDBCCodingApp()),
    )
  }


  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      AppServer(localName = "localhost", "localhost", 37600)._main(args)
    }
  }

}



