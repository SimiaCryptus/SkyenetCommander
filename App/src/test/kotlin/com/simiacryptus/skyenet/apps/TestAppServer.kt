package com.simiacryptus.skyenet.apps

import com.simiacryptus.skyenet.AppServer
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.AuthenticationInterface
import com.simiacryptus.skyenet.core.platform.AuthorizationInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
import com.simiacryptus.skyenet.core.util.AwsUtil
import com.simiacryptus.skyenet.webui.servlet.OAuthGoogle

object TestAppServer : AppServer(
  publicName = "localhost",
  localName = "localhost",
  port = 37600,/*Random.nextInt(1024, 65535)*/
) {

  @JvmStatic
  fun main(args: Array<String>) {
    super._main(args)
  }

  override fun setupPlatform() {
    val mockUser = User(
      "1",
      "user@mock.test",
      "Test User",
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


}
