package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.apps.openapi.OpenApi
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.interpreter.Interpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ToolServlet
import com.simiacryptus.skyenet.webui.session.SessionTask
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.slf4j.LoggerFactory
import java.util.function.Function
import kotlin.reflect.KClass

open class ToolAgent<T : Interpreter>(
  api: API,
  dataStorage: StorageInterface,
  session: Session,
  user: User?,
  ui: ApplicationInterface,
  interpreter: KClass<T>,
  symbols: Map<String, Any>,
  temperature: Double = 0.1,
  details: String? = null,
  model: ChatModels = ChatModels.GPT35Turbo,
  actorMap: Map<ActorTypes, CodingActor> = mapOf(
    ActorTypes.CodingActor to CodingActor(
      interpreter,
      symbols = symbols,
      temperature = temperature,
      details = details,
      model = model
    )
  ),
) : CodingAgent<T>(api, dataStorage, session, user, ui, interpreter, symbols, temperature, details, model, actorMap) {
  override fun displayFeedback(task: SessionTask, request: CodingActor.CodeRequest, response: CodingActor.CodeResult) {
    val formText = StringBuilder()
    var formHandle: StringBuilder? = null
    formHandle = task.add(
      """
      |<div style="display: flex;flex-direction: column;">
      |${super.playButton(task, request, response, formText) { formHandle!! }}
      |${super.regenButton(task, request, formText) { formHandle!! }}
      |${createToolButton(task, request, response, formText) { formHandle!! }}
      |</div>  
      |${super.reviseMsg(task, request, response, formText) { formHandle!! }}
      """.trimMargin(), className = "reply-message"
    )
    formText.append(formHandle.toString())
    formHandle.toString()
    task.complete()
  }

  protected fun createToolButton(
    task: SessionTask,
    request: CodingActor.CodeRequest,
    response: CodingActor.CodeResult,
    formText: StringBuilder,
    formHandle: () -> StringBuilder
  ) = ui.hrefLink("\uD83D\uDCE4", "href-link regen-button") {
    responseAction(task, "Exporting...", formHandle(), formText) {
      val servletHandler = answer(
        object : CodingActor(
          interpreterClass = actor.interpreterClass,
          symbols = actor.symbols + mapOf(
            "request" to Request(null, null),
            "response" to Response(null, null)
          ),
          describer = object : AbbrevWhitelistYamlDescriber(
            "com.simiacryptus",
            "com.github.simiacryptus"
          ) {
            override fun describe(rawType: Class<in Nothing>, stackMax: Int): String = when(rawType) {
              Request::class.java -> describe(HttpServletRequest::class.java)
              Response::class.java -> describe(HttpServletResponse::class.java)
              else -> super.describe(rawType, stackMax)
            }
          },
          details = actor.details,
          model = actor.model,
          fallbackModel = actor.fallbackModel,
          temperature = actor.temperature,
          runtimeSymbols = actor.runtimeSymbols
        ) {
          override val prompt: String
            get() = super.prompt
        }, request.copy(
          messages = listOf(
            response.code to ApiModel.Role.assistant,
            "Reprocess this code as a doPost method implementation (sans wrapping code)" to ApiModel.Role.user
          )
        ), task
      )
      val openAPISpec = ParsedActor(
        parserClass = OpenApiParser::class.java,
        prompt = "You are a code documentation assistant. You will create the OpenAPI definition for a POST servlet handler written in kotlin"
      ).answer(listOf(servletHandler.code), api)
      val reJson = JsonUtil.toJson(openAPISpec.obj)
      task.add(reJson)
      ToolServlet.tools.add(
        ToolServlet.Tool(
          path = "/${openAPISpec.hashCode()}",
          openApiDescription = reJson,
          code = servletHandler.code,
          interpreterClass = actor.interpreterClass.java,
          symbols = actor.symbols
        )
      )
    }
  }

  interface OpenApiParser : Function<String, OpenApi> {
    @Description("Extract OpenAPI spec")
    override fun apply(t: String): OpenApi
  }

  private fun answer(
    actor: CodingActor,
    request: CodingActor.CodeRequest,
    task: SessionTask = ui.newTask()
  ): CodingActor.CodeResult {
    val response = actor.answer(request, api = api)
    displayCode(task, request, response)
    return response
  }

  companion object {
    val log = LoggerFactory.getLogger(ToolAgent::class.java)
  }
}