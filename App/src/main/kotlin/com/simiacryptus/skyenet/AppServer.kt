package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.apps.coding.AwsCodingApp
import com.simiacryptus.skyenet.apps.coding.BashCodingApp
import com.simiacryptus.skyenet.apps.coding.GmailCodingApp
import com.simiacryptus.skyenet.apps.coding.PowershellCodingApp
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.AuthenticationInterface
import com.simiacryptus.skyenet.core.platform.AuthorizationInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
import com.simiacryptus.skyenet.webui.application.ApplicationDirectory
import com.simiacryptus.skyenet.webui.application.InterpreterAndTools
import com.simiacryptus.skyenet.webui.application.ToolServlet
import com.simiacryptus.skyenet.webui.servlet.OAuthBase
import org.eclipse.jetty.webapp.WebAppContext


open class AppServer(
  localName: String, publicName: String, port: Int
) : ApplicationDirectory(
  localName = localName, publicName = publicName, port = port
) {

  override val toolServlet = object : ToolServlet(this@AppServer) {
    override fun fromString(str: String): InterpreterAndTools {
      val parts = str.split(":", limit = 2)
      return when (Class.forName(parts[0]) as Class) {
        AwsCodingApp::class.java -> AwsCodingApp.fromString(if(parts.size > 1) parts[1] else "")
        GmailCodingApp::class.java -> GmailCodingApp.fromString(if(parts.size > 1) parts[1] else "")
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
    )
  }

  override fun authenticatedWebsite() = object : OAuthBase("") {
    override fun configure(context: WebAppContext, addFilter: Boolean) = context
  }

  override fun setupPlatform() {
    super.setupPlatform()
    val mockUser = User(
      "1",
      "user@local",
      "Local User",
      ""
    )
    ApplicationServices.authenticationManager = object : AuthenticationInterface {
      override fun getUser(accessToken: String?) = mockUser
      override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
      override fun logout(accessToken: String, user: User) {}
    }
    ApplicationServices.authorizationManager = object : AuthorizationManager() {
      override fun isAuthorized(
        applicationClass: Class<*>?,
        user: User?,
        operationType: AuthorizationInterface.OperationType
      ): Boolean = true
    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      AppServer(localName = "localhost", "localhost", 37600)._main(args)
    }
  }

}



