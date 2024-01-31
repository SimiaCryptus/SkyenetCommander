package com.simiacryptus.skyenet

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.security.GeneralSecurityException

open class GmailService(
  val applicationName: String = "Gmail API Java Quickstart",
  val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance(),
  val tokensDir: String = "tokens",
  val credentialsResourcePath: String = "/google-credentials.json",
  val scopes: List<String> = listOf(
    GmailScopes.GMAIL_LABELS,
    GmailScopes.GMAIL_READONLY,
    GmailScopes.MAIL_GOOGLE_COM,
  ),
) {
  open val transport by lazy { GoogleNetHttpTransport.newTrustedTransport() }

  @Throws(IOException::class)
  open fun getCredentials(transport: NetHttpTransport) =
    AuthorizationCodeInstalledApp(
      GoogleAuthorizationCodeFlow.Builder(
        transport,
        jsonFactory,
        GoogleClientSecrets.load(
          jsonFactory,
          getCredentialsJsonStream()
        ), scopes
      )
        .setDataStoreFactory(FileDataStoreFactory(File(tokensDir)))
        .setAccessType("offline")
        .build(), LocalServerReceiver.Builder().setPort(8888).build()
    ).authorize("user")

  open fun getCredentialsJsonStream() = InputStreamReader(
    GmailService::class.java.getResourceAsStream(credentialsResourcePath)
      ?: throw FileNotFoundException("Resource not found: $credentialsResourcePath")
  )

  open fun getGmailService() = Gmail
    .Builder(transport, jsonFactory, getCredentials(transport))
    .setApplicationName(applicationName)
    .build()

  companion object : GmailService() {

    @Throws(IOException::class, GeneralSecurityException::class)
    @JvmStatic
    fun main(args: Array<String>) {
      val service: Gmail = getGmailService()
      val user: String = "me"
      val users: Gmail.Users = service.users()
      val messageSvc: Gmail.Users.Messages = users.messages()
      val labelSvc: Gmail.Users.Labels = users.labels()
      val labels = labelSvc.list(user).execute().labels
      labels.forEach { println(it) }
      val listRequest = messageSvc.list(user)
      val listMessagesResponse = listRequest.execute()
      val messages = listMessagesResponse.messages
      messages.forEach {
        val message = messageSvc.get(user, it.id).execute()
        message.payload.headers.forEach {
          println(it)
        }
      }
    }
  }

}