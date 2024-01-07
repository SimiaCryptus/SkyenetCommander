package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.interpreter.ProcessInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import java.io.File

class BashCodingApp(
) : ApplicationServer(
  "Bash Coding Assistant v1.0") {

  data class Settings(
    val env: Map<String, String> = mapOf(),
    val workingDir: String = ".",
    val model: ChatModels = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
    val language: String = "bash",
    val command: List<String> = listOf("bash"),
  )
  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val settings = getSettings<Settings>(session, user)
    object : CodingAgent<ProcessInterpreter>(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = ProcessInterpreter::class,
      symbols =   mapOf(
        "env" to (settings?.env ?: mapOf()),
        "workingDir" to File(settings?.workingDir ?: ".").absolutePath,
        "language" to (settings?.language ?: "bash"),
        "command" to (settings?.command ?: listOf("bash")),
      ),
      temperature = (settings?.temperature ?: 0.1),
      model = (settings?.model ?: ChatModels.GPT35Turbo),
    ) {
      override fun displayFeedback(
        task: SessionTask,
        request: CodingActor.CodeRequest,
        response: CodingActor.CodeResult
      ) {
        var formHandle: StringBuilder? = null
        val playHandler: (t: Unit) -> Unit = {
          formHandle?.clear()
          val header = task.header("Running...")
          try {
            header?.clear()
            val resultValue = response.result.resultValue
            task.add(renderMarkdown(resultValue))
            displayFeedback(task, CodingActor.CodeRequest(
              messages = request.messages +
                  listOf(
                    "Running...\n\n$resultValue" to ApiModel.Role.assistant,
                  ).filter { it.first.isNotBlank() }
            ), response)
          } catch (e: Throwable) {
            header?.clear()
            val message = when {
              e is ValidatedObject.ValidationError -> renderMarkdown(e.message ?: "")
              e is CodingActor.FailedToImplementException -> renderMarkdown(
                """
                  |**Failed to Implement** 
                  |
                  |${e.message}
                  |
                  |""".trimMargin()
              )

              else -> renderMarkdown(
                """
                  |**Error `${e.javaClass.name}`**
                  |
                  |```text
                  |${e.message}
                  |```
                  |""".trimMargin()
              )
            }
            task.add(message, true, "div", "error")
            val codeRequest = CodingActor.CodeRequest(
              messages = request.messages +
                  listOf(
                    response.code to ApiModel.Role.assistant,
                    message to ApiModel.Role.system,
                  ).filter { it.first.isNotBlank() }
            )
            super.displayCode(task, codeRequest, actor.answer(codeRequest, api = api))
          }
        }
        val feedbackHandler: (t: String) -> Unit = { feedback ->
          try {
            formHandle?.clear()
            task.echo(renderMarkdown(feedback))
            val codeRequest = CodingActor.CodeRequest(
              messages = request.messages +
                  listOf(
                    response?.code to ApiModel.Role.assistant,
                    feedback to ApiModel.Role.user,
                  ).filter { it.first?.isNotBlank() == true }.map { it.first!! to it.second }
            )
            super.displayCode(task, codeRequest, actor.answer(codeRequest, api = api))
          } catch (e: Throwable) {
            log.warn("Error", e)
            task.error(e)
          }
        }
        formHandle = task.add("""
          |<div class='code-execution'>
          |    ${if (super.canPlay) super.ui.hrefLink("▶", "href-link play-button", playHandler) else ""}
          |    ${super.ui.textInput(feedbackHandler)}
          |</div>
        """.trimMargin(), className = "reply-message"
        )
        task.complete()
      }
    }.start(
      userMessage = userMessage,
    )
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(BashCodingApp::class.java)
  }
}