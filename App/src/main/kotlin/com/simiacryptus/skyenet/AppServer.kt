package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.apps.coding.AwsCodingApp
import com.simiacryptus.skyenet.apps.coding.BashCodingApp
import com.simiacryptus.skyenet.apps.coding.GmailCodingApp
import com.simiacryptus.skyenet.webui.application.ApplicationDirectory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest


open class AppServer(
  localName: String, publicName: String, port: Int
) : ApplicationDirectory(
  localName = localName, publicName = publicName, port = port
) {

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      AppServer(localName = "localhost", "localhost", 37600)._main(args)
    }
  }

  //    private val sparkConf = SparkConf().setMaster("local[*]").setAppName("Spark Coding Assistant")
  override val childWebApps by lazy {
    listOf(
      ChildWebApp("/aws", AwsCodingApp()),
      ChildWebApp("/gmail", GmailCodingApp()),
      ChildWebApp("/bash", BashCodingApp()),
    )
  }

  private fun fetchPlaintextSecret(secretArn: String, region: Region) =
    SecretsManagerClient.builder()
      .region(region)
      .credentialsProvider(DefaultCredentialsProvider.create())
      .build().getSecretValue(
        GetSecretValueRequest.builder()
          .secretId(secretArn)
          .build()
      ).secretString()

}



