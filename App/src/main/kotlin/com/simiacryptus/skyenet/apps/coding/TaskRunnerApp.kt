package com.simiacryptus.skyenet.apps.coding

import com.github.simiacryptus.aicoder.util.addApplyDiffLinks2
import com.github.simiacryptus.aicoder.util.addSaveLinks
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.ApiModel.Role
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.Acceptable
import com.simiacryptus.skyenet.AgentPatterns.displayMapInTabs
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.ApplicationServices.clientManager
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference


class TaskRunnerApp(
  applicationName: String = "Task Planning v1.0",
  path: String = "/taskDev",
) : ApplicationServer(
  applicationName = applicationName,
  path = path,
) {
  data class Settings(
    val model: ChatModels = ChatModels.GPT4Turbo,
    val parsingModel: ChatModels = ChatModels.GPT4Turbo,
    val temperature: Double = 0.2,
    val budget: Double = 2.0,
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T = Settings() as T

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val settings = getSettings<Settings>(session, user)
      if (api is ClientManager.MonitoredClient) api.budget = settings?.budget ?: 2.0
      TaskRunnerAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT4Turbo,
        parsingModel = settings?.parsingModel ?: ChatModels.GPT4Turbo,
        temperature = settings?.temperature ?: 0.3,
      ).startProcess(userMessage = userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(TaskRunnerApp::class.java)
    val agents = mutableMapOf<Session, TaskRunnerApp>()
  }
}

class TaskRunnerAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT4Turbo,
  parsingModel: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
  val actorMap: Map<ActorTypes, BaseActor<*, *>> = mapOf(
    ActorTypes.TaskBreakdown to ParsedActor(
      resultClass = TaskBreakdownResult::class.java,
      prompt = """
        Given a user request, identify and list smaller, actionable tasks that can be directly implemented in code.
        Detail task dependencies and relationships, and ensure the tasks are well-organized and logically ordered.
        Briefly explain your rationale for the task breakdown and ordering.
        
        Tasks can be of the following types: 
        * TaskPlanning - High-level planning and organization of tasks - identify smaller, actionable tasks based on the information available at task execution time.
          ** Specify the prior tasks and the goal of the task
        * Inquiry - Answer questions by reading in files and providing a summary that can be discussed with and approved by the user
          ** Specify the questions and the goal of the inquiry
          ** List input files to be examined
        * NewFile - Create one or more new files
          ** For each file, specify the relative file path and the purpose of the file
          ** List input files/tasks to be examined
        * EditFile - Modify existing files
          ** For each file, specify the relative file path and the goal of the modification
          ** List input files/tasks to be examined
        * Documentation - Generate documentation
          ** List input files/tasks to be examined
        
      """.trimIndent(),
      model = model,
      parsingModel = parsingModel,
      temperature = temperature,
    ),
    ActorTypes.DocumentationGenerator to SimpleActor(
      prompt = """
        Create detailed and clear documentation for the provided code, covering its purpose, functionality, inputs, outputs, and any assumptions or limitations.
        Use a structured and consistent format that facilitates easy understanding and navigation. 
        Include code examples where applicable, and explain the rationale behind key design decisions and algorithm choices.
        Document any known issues or areas for improvement, providing guidance for future developers on how to extend or maintain the code.
      """.trimIndent(),
      model = model,
      temperature = temperature,
    ),
    ActorTypes.NewFileCreator to SimpleActor(
      prompt = """
        Generate the necessary code for a new file based on the given requirements and context. 
        Ensure the code is well-structured, follows best practices, and meets the specified functionality. 
        Provide a clear file name suggestion based on the content and purpose of the file.
          
        Response should use one or more ``` code blocks to output file contents.
        Triple backticks should be bracketed by newlines and an optional the language identifier.
        Each file should be preceded by a header that identifies the file being modified.
        
        Example:
        
        Explanation text
        
        ### scripts/filename.js
        ```js
        
        const b = 2;
        function exampleFunction() {
          return b + 1;
        }
        
        ```
        
        Continued text
      """.trimIndent(),
      model = model,
      temperature = temperature,
    ),
    ActorTypes.FilePatcher to SimpleActor(
      prompt = """
        Generate a patch for an existing file to modify its functionality or fix issues based on the given requirements and context. 
        Ensure the modifications are efficient, maintain readability, and adhere to coding standards. 
        Provide a summary of the changes made.
          
        Response should use one or more code patches in diff format within ```diff code blocks.
        Each diff should be preceded by a header that identifies the file being modified.
        The diff format should use + for line additions, - for line deletions.
        The diff should include 2 lines of context before and after every change.
        
        Example:
        
        Explanation text
        
        ### scripts/filename.js
        ```diff
        - const b = 2;
        + const a = 1;
        ```

        Consider the following task types: TaskPlanning, Requirements, NewFile, EditFile, and Documentation.
        Ensure that each identified task fits one of these categories and specify the task type for better integration with the system.
        Continued text
      """.trimIndent(),
      model = model,
      temperature = temperature,
    ),
    ActorTypes.Inquiry to SimpleActor(
      prompt = """
        Create code for a new file that fulfills the specified requirements and context.
        Given a detailed user request, break it down into smaller, actionable tasks suitable for software development.
        Compile comprehensive information and insights on the specified topic.
        Provide a comprehensive overview, including key concepts, relevant technologies, best practices, and any potential challenges or considerations. 
        Ensure the information is accurate, up-to-date, and well-organized to facilitate easy understanding.

        Focus on generating insights and information that support the task types available in the system (TaskPlanning, Requirements, NewFile, EditFile, Documentation).
        This will ensure that the inquiries are tailored to assist in the planning and execution of tasks within the system's framework.
     """.trimIndent(),
      model = model,
      temperature = temperature,
    ),
  ),
) : ActorSystem<TaskRunnerAgent.ActorTypes>(
  actorMap.map { it.key.name to it.value }.toMap(),
  dataStorage,
  user,
  session
) {
  val documentationGeneratorActor by lazy { actorMap[ActorTypes.DocumentationGenerator] as SimpleActor }
  val taskBreakdownActor by lazy { actorMap[ActorTypes.TaskBreakdown] as ParsedActor<TaskBreakdownResult> }
  val newFileCreatorActor by lazy { actorMap[ActorTypes.NewFileCreator] as SimpleActor }
  val filePatcherActor by lazy { actorMap[ActorTypes.FilePatcher] as SimpleActor }
  val inquiryActor by lazy { actorMap[ActorTypes.Inquiry] as SimpleActor }

  data class TaskBreakdownResult(
    val tasksByID: Map<String, Task>? = null,
    val finalTaskID: String? = null,
  )

  data class Task(
    val description: String? = null,
    val taskType: TaskType? = null,
    var task_dependencies: List<String>? = null,
    val input_files: List<String>? = null,
    val output_files: List<String>? = null,
    var state: TaskState? = null,
  )

  enum class TaskState {
    Pending,
    InProgress,
    Completed,
  }

  enum class TaskType {
    TaskPlanning,
    Inquiry,
    NewFile,
    EditFile,
    Documentation,
  }

  val root = dataStorage.getSessionDir(user, session).toPath()
  val codeFiles get() = mutableMapOf<String, String>().apply {
    root.toFile().walk().forEach { file ->
      if (file.isFile && file.length() < 1e6) {
        put(root.relativize(file.toPath()).toString(), file.readText())
      }
    }
  }

  fun startProcess(userMessage: String) {
    val eventStatus = ""
      """
      |Root: ${root.toFile().absolutePath}
      |
      |Files:
      |${codeFiles.keys.joinToString("\n") { "* ${it}" }}  
    """.trimMargin()

    val task = ui.newTask()
    val toInput = { it: String ->
      listOf(
        eventStatus,
        it
      )
    }
    val highLevelPlan = Acceptable(
      task = task,
      userMessage = userMessage,
      initialResponse = { it: String -> taskBreakdownActor.answer(toInput(it), api = api) },
      outputFn = { design: ParsedResponse<TaskBreakdownResult> ->
          displayMapInTabs(mapOf(
            "Text" to renderMarkdown(design.text),
            "JSON" to renderMarkdown("```json\n${toJson(design.obj)}\n```"),
            "Flow" to renderMarkdown("## Task Graph\n```mermaid\n${buildMermaidGraph(design.obj.tasksByID?.toMutableMap() ?: mutableMapOf())}\n```")
          ))
        },
      ui = ui,
      reviseResponse = { userMessages: List<Pair<String, Role>> ->
        taskBreakdownActor.respond(
          messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }.toTypedArray<ApiModel.ChatMessage>()),
          input = toInput(userMessage),
          api = api
        )
      },
      atomicRef = AtomicReference(),
      semaphore = Semaphore(0),
      heading = userMessage
    ).call()

    try {
      val pool: ThreadPoolExecutor = clientManager.getPool(session, user, dataStorage)
      val genState = GenState(highLevelPlan.obj.tasksByID?.toMutableMap() ?: mutableMapOf())
      val taskTabs = object : TabbedDisplay(task) {
       override fun renderTabButtons(): String {
         return buildString {
           append("<div class='tabs'>\n")
           super.tabs.withIndex().forEach { (idx, t) ->
             val (taskId, taskV) = t
             val subTask = highLevelPlan.obj.tasksByID?.get(taskId)
             val isChecked = if (taskId in genState.taskIds) "checked" else ""
             val style = when(subTask?.state) {
               TaskState.Completed -> " style='text-decoration: line-through;'"
               null -> " style='opacity: 20%;'"
               TaskState.Pending -> " style='opacity: 30%;'"
               else -> ""
             }
             append("<label class='tab-button' data-for-tab='${idx}'$style><input type='checkbox' $isChecked disabled /> $taskId</label><br/>\n")
           }
           append("</div>")
         }
       }
      }
      genState.taskIds.forEach { taskId ->
        val newTask = ui.newTask()
        genState.taskMap[taskId] = newTask
        taskTabs[genState.subTasks[taskId]?.description ?: taskId] = "<div id=${newTask.operationID}></div>"
      }
      Thread.sleep(100)
      while (genState.taskIds.isNotEmpty()) {
        val taskId = genState.taskIds.removeAt(0)
        val subTask = genState.subTasks[taskId] ?: throw RuntimeException("Task not found: $taskId")
        taskTabs[subTask.description ?: taskId] ?: genState.taskMap.apply {
          val newTask = ui.newTask()
          put(taskId, newTask)
          taskTabs[subTask.description ?: taskId] = "<div id=${newTask.operationID}></div>"
        }
        subTask.task_dependencies
          ?.associate { it to genState.taskFutures[it] }
          ?.forEach { (id, future) ->
            try {
              future?.get() ?: log.warn("Dependency not found: $id")
            } catch (e: Throwable) {
              log.warn("Error", e)
            }
          }
        genState.taskFutures[taskId] = pool.submit {
          runTask(
            taskId = taskId,
            subTask = subTask,
            userMessage = userMessage,
            highLevelPlan = highLevelPlan,
            genState = genState,
            task = genState.taskMap.get(taskId) ?: ui.newTask(),
            taskTabs = taskTabs
          )
        }
      }
      genState.taskFutures.forEach { (id, future) ->
        try {
          future.get() ?: log.warn("Dependency not found: $id")
        } catch (e: Throwable) {
          log.warn("Error", e)
        }
      }
    } catch (e: Throwable) {
      log.warn("Error during incremental code generation process", e)
      task.error(ui, e)
    }
  }

  data class GenState(
    val subTasks: MutableMap<String, Task>,
    val taskIds: MutableList<String> = executionOrder(subTasks).toMutableList(),
    val replyText: MutableMap<String, String> = mutableMapOf(),
    val completedTasks: MutableList<String> = mutableListOf(),
    val taskFutures: MutableMap<String, Future<*>> = mutableMapOf(),
    val taskMap : MutableMap<String, SessionTask> = mutableMapOf(),
  )

  private fun runTask(
    taskId: String,
    subTask: Task,
    userMessage: String,
    highLevelPlan: ParsedResponse<TaskBreakdownResult>,
    genState: GenState,
    task: SessionTask,
    taskTabs: TabbedDisplay,
  ) {
    try {
      subTask.state = TaskState.InProgress
      taskTabs.update()
      val dependencies = subTask.task_dependencies?.toMutableSet() ?: mutableSetOf()
      dependencies += getAllDependencies(subTask, genState.subTasks)
      val priorCode = dependencies
        .joinToString("\n\n\n") { dependency ->
          """
          |# $dependency
          |
          |${genState.replyText[dependency] ?: ""}
          """.trimMargin()
        }
      val inputFileCode = subTask.input_files?.joinToString("\n\n\n") {
        """
        |# $it
        |
        |```
        |${codeFiles[it]}
        |```
        """.trimMargin()
      } ?: ""
      task.add(
        renderMarkdown(
          """
          |## Task `${taskId}`
          |${subTask.description ?: ""}
          |
          |```json
          |${toJson(subTask)}
          |```
          |
          |### Dependencies:
          |${dependencies.joinToString("\n") { "- $it" }}
          |
          """.trimMargin()
        )
      )

      when (subTask.taskType) {

        TaskType.NewFile -> {
          val semaphore = Semaphore(0)
          createFiles(
            task,
            userMessage,
            highLevelPlan,
            priorCode,
            inputFileCode,
            subTask,
            genState,
            taskId
          ) { semaphore.release() }
          try {
            semaphore.acquire()
          } catch (e: Throwable) {
            log.warn("Error", e)
          }

        }

        TaskType.EditFile -> {
          val semaphore = Semaphore(0)
          editFiles(
            task,
            userMessage,
            highLevelPlan,
            priorCode,
            inputFileCode,
            subTask,
            genState,
            taskId
          ) { semaphore.release() }
          try {
            semaphore.acquire()
          } catch (e: Throwable) {
            log.warn("Error", e)
          }
        }

        TaskType.Documentation -> {
          val semaphore = Semaphore(0)
          document(
            task,
            userMessage,
            highLevelPlan,
            priorCode,
            inputFileCode,
            genState,
            taskId
          ) {
            semaphore.release()
          }
          try {
            semaphore.acquire()
          } catch (e: Throwable) {
            log.warn("Error", e)
          }
        }

        TaskType.Inquiry -> {
          inquiry(
            subTask,
            userMessage,
            highLevelPlan,
            priorCode,
            inputFileCode,
            genState,
            taskId,
            task
          )
        }

        TaskType.TaskPlanning -> {
          taskPlanning(
            subTask = subTask,
            userMessage = userMessage,
            highLevelPlan = highLevelPlan,
            priorCode = priorCode,
            inputFileCode = inputFileCode,
            genState = genState,
            taskId = taskId,
            task = task,
            taskTabs = taskTabs,
          )
        }

        else -> null
      }
    } catch (e: Exception) {
      log.warn("Error during task execution", e)
      task.error(ui, e)
    } finally {
      genState.completedTasks.add(taskId)
      subTask.state = TaskState.Completed
      taskTabs.update()
    }
  }

  private fun createFiles(
    task: SessionTask,
    userMessage: String,
    highLevelPlan: ParsedResponse<TaskBreakdownResult>,
    priorCode: String,
    inputFileCode: String,
    subTask: Task,
    genState: GenState,
    taskId: String,
    onComplete: () -> Unit
  ) {

    val process = { sb : StringBuilder ->
      val codeResult = newFileCreatorActor.answer(
        listOf(
          userMessage,
          highLevelPlan.text,
          priorCode,
          inputFileCode,
          subTask.description ?: "",
        ), api
      )
      genState.replyText[taskId] = codeResult
      renderMarkdown(ui.socketManager.addSaveLinks(codeResult, task) {  path, newCode ->
        val prev = codeFiles[path]
        if (prev != newCode) {
          codeFiles[path] = newCode
          val bytes = newCode.toByteArray(Charsets.UTF_8)
          task.complete("<a href='${task.saveFile(path, bytes)}'>$path</a> Created")
        } else {
          task.complete("No changes to $path")
        }
      }) + accept(sb) {
        task.complete()
        onComplete()
      }
    }
    object : Retryable(ui, task, process) {
      init {
        addTab(ui, process(container!!))
      }
    }
  }

  private fun editFiles(
    task: SessionTask,
    userMessage: String,
    highLevelPlan: ParsedResponse<TaskBreakdownResult>,
    priorCode: String,
    inputFileCode: String,
    subTask: Task,
    genState: GenState,
    taskId: String,
    onComplete: () -> Unit
  ) {
    val process = { sb : StringBuilder ->
      val codeResult = filePatcherActor.answer(
        listOf(
          userMessage,
          highLevelPlan.text,
          priorCode,
          inputFileCode,
          subTask.description ?: "",
        ), api
      )
      genState.replyText[taskId] = codeResult
      renderMarkdown(ui.socketManager.addApplyDiffLinks2(codeFiles, codeResult, handle = { newCodeMap ->
        newCodeMap.forEach { (path, newCode) ->
          val prev = codeFiles[path]
          if (prev != newCode) {
            codeFiles[path] = newCode
            task.complete(
              "<a href='${
                task.saveFile(
                  path,
                  newCode.toByteArray(Charsets.UTF_8)
                )
              }'>$path</a> Updated"
            )
          }
        }
      }, task = task)) + accept(sb) {
        task.complete()
        onComplete()
      }
    }
    object : Retryable(ui, task, process){
      init {
        addTab(ui, process(container!!))
      }
    }
  }

  private fun document(
    task: SessionTask,
    userMessage: String,
    highLevelPlan: ParsedResponse<TaskBreakdownResult>,
    priorCode: String,
    inputFileCode: String,
    genState: GenState,
    taskId: String,
    onComplete: () -> Unit
  ) {
    val process = { sb : StringBuilder ->
      val docResult = documentationGeneratorActor.answer(
        listOf(
          userMessage,
          highLevelPlan.text,
          priorCode,
          inputFileCode,
        ), api
      )
      genState.replyText[taskId] = docResult
      renderMarkdown("## Generated Documentation\n$docResult") + accept(sb) {
        task.complete()
        onComplete()
      }
    }
    object : Retryable(ui, task, process){
      init {
        addTab(ui, process(container!!))
      }
    }
  }

  private fun accept(stringBuilder: StringBuilder, fn: () -> Unit): String {
    val startTag = """<!-- BEGIN ACCEPT LINK -->"""
    val endTag = """<!-- END ACCEPT LINK -->"""
    return startTag + ui.hrefLink("Accept") {
      try {
        val prev = stringBuilder.toString()
        require(prev.contains(startTag) && prev.contains(endTag)) {
          "Accept link not found"
        }
        val newValue = prev.substringBefore(startTag) + "Accepted" + prev.substringAfter(endTag)
        stringBuilder.clear()
        stringBuilder.append(newValue)
      } catch (e: Throwable) {
        log.warn("Error", e)
      }
      fn()
    } + endTag
  }

  private fun inquiry(
    subTask: Task,
    userMessage: String,
    highLevelPlan: ParsedResponse<TaskBreakdownResult>,
    priorCode: String,
    inputFileCode: String,
    genState: GenState,
    taskId: String,
    task: SessionTask
  ) {
    val input1 = "Expand ${subTask.description ?: ""}"
    val toInput = { it: String ->
      listOf(
        userMessage,
        highLevelPlan.text,
        priorCode,
        inputFileCode,
        it,
      )
    }
    val inquiryResult = Acceptable(
      task = task,
      userMessage = input1,
      initialResponse = { it: String -> inquiryActor.answer(toInput(it), api = api) },
      outputFn = { design: String ->
          renderMarkdown(design)
        },
      ui = ui,
      reviseResponse = { userMessages: List<Pair<String, Role>> ->
        inquiryActor.respond(
          messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }.toTypedArray<ApiModel.ChatMessage>()),
          input = toInput(input1),
          api = api
        )
      },
      atomicRef = AtomicReference(),
      semaphore = Semaphore(0),
      heading = "Expand ${subTask.description ?: ""}"
    ).call()
    genState.replyText[taskId] = inquiryResult
  }

  private fun taskPlanning(
    subTask: Task,
    userMessage: String,
    highLevelPlan: ParsedResponse<TaskBreakdownResult>,
    priorCode: String,
    inputFileCode: String,
    genState: GenState,
    taskId: String,
    task: SessionTask,
    taskTabs: TabbedDisplay
  ) {
    val input1 = "Expand ${subTask.description ?: ""}"
    val toInput = { it: String ->
      listOf(
        userMessage,
        highLevelPlan.text,
        priorCode,
        inputFileCode,
        it
      )
    }
    val subPlan = Acceptable(
      task = task,
      userMessage = input1,
      initialResponse = { it: String -> taskBreakdownActor.answer(toInput(it), api = api) },
      outputFn = { design: ParsedResponse<TaskBreakdownResult> ->
          displayMapInTabs(mapOf(
            "Text" to renderMarkdown(design.text),
            "JSON" to renderMarkdown("```json\n${toJson(design.obj)}\n```"),
          ))
        },
      ui = ui,
      reviseResponse = { userMessages: List<Pair<String, Role>> ->
        taskBreakdownActor.respond(
          messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }.toTypedArray<ApiModel.ChatMessage>()),
          input = toInput(input1),
          api = api
        )
      },
      atomicRef = AtomicReference(),
      semaphore = Semaphore(0),
      heading = "Expand ${subTask.description ?: ""}"
    ).call()
    genState.replyText[taskId] = subPlan.text
    var newTasks = subPlan.obj.tasksByID
    newTasks?.forEach {
      val newTask = ui.newTask()
      genState.taskMap[it.key] = newTask
      taskTabs[it.key] = "<div id=${newTask.operationID}></div>"
    }
    val conflictingKeys = newTasks?.keys?.intersect(genState.subTasks.keys)
    newTasks = newTasks?.entries?.associate { (key, value) ->
      (when {
        conflictingKeys?.contains(key) == true -> "${taskId}_${key}"
        else -> key
      }) to value.copy(task_dependencies = value.task_dependencies?.map { key ->
        when {
          conflictingKeys?.contains(key) == true -> "${taskId}_${key}"
          else -> key
        }
      })
    }
    genState.subTasks.putAll(newTasks ?: emptyMap())
    executionOrder(newTasks ?: emptyMap()).reversed().forEach { genState.taskIds.add(0, it) }
    genState.subTasks.values.forEach {
      it.task_dependencies = it.task_dependencies?.map { dep ->
        when {
          dep == taskId -> subPlan.obj.finalTaskID ?: dep
          else -> dep
        }
      }
    }
    task.complete(renderMarkdown("## Task Dependency Graph\n```mermaid\n${buildMermaidGraph(genState.subTasks)}\n```"))
  }

  private fun getAllDependencies(subTask: Task, subTasks: MutableMap<String, Task>): List<String> {
    return getAllDependenciesHelper(subTask, subTasks, mutableSetOf())
  }

  private fun getAllDependenciesHelper(
    subTask: Task,
    subTasks: MutableMap<String, Task>,
    visited: MutableSet<String>
  ): List<String> {
    val dependencies = subTask.task_dependencies?.toMutableList() ?: mutableListOf()
    subTask.task_dependencies?.forEach { dep ->
      if (dep in visited) return@forEach
      val subTask = subTasks[dep]
      if (subTask != null) {
        visited.add(dep)
        dependencies.addAll(getAllDependenciesHelper(subTask, subTasks, visited))
      }
    }
    return dependencies
  }

  private fun buildMermaidGraph(subTasks: Map<String, Task>): String {
    val graphBuilder = StringBuilder("graph TD;\n")
    subTasks.forEach { (taskId, task) ->
      val sanitizedTaskId = sanitizeForMermaid(taskId)
      val taskType = task.taskType?.name ?: "Unknown"
      val escapedDescription = escapeMermaidCharacters(task.description ?: "")
      graphBuilder.append("    ${sanitizedTaskId}[$escapedDescription]:::$taskType;\n")
      task.task_dependencies?.forEach { dependency ->
        val sanitizedDependency = sanitizeForMermaid(dependency)
        graphBuilder.append("    ${sanitizedDependency} --> ${sanitizedTaskId};\n")
      }
    }
    graphBuilder.append("    classDef default fill:#f9f9f9,stroke:#333,stroke-width:2px;\n")
    graphBuilder.append("    classDef NewFile fill:lightblue,stroke:#333,stroke-width:2px;\n")
    graphBuilder.append("    classDef EditFile fill:lightgreen,stroke:#333,stroke-width:2px;\n")
    graphBuilder.append("    classDef Documentation fill:lightyellow,stroke:#333,stroke-width:2px;\n")
    graphBuilder.append("    classDef Inquiry fill:orange,stroke:#333,stroke-width:2px;\n")
    graphBuilder.append("    classDef TaskPlanning fill:lightgrey,stroke:#333,stroke-width:2px;\n")
    return graphBuilder.toString()
  }

  private fun sanitizeForMermaid(input: String): String {
    return input.replace(" ", "_")
      .replace("\"", "\\\"")
      .replace("[", "\\[")
      .replace("]", "\\]")
      .replace("(", "\\(")
      .replace(")", "\\)")
  }

  private fun escapeMermaidCharacters(input: String): String {
    return input
  }

  enum class ActorTypes {
    TaskBreakdown,
    DocumentationGenerator,
    NewFileCreator,
    FilePatcher,
    Inquiry,
  }

  companion object {
    private val log = LoggerFactory.getLogger(TaskRunnerAgent::class.java)
    fun executionOrder(tasks: Map<String, Task>): List<String> {
      val taskIds: MutableList<String> = mutableListOf()
      val taskMap = tasks.toMutableMap()
      while (taskMap.isNotEmpty()) {
        val nextTasks = taskMap.filter { (_, task) -> task.task_dependencies?.all { taskIds.contains(it) } ?: true }
        if (nextTasks.isEmpty()) {
          throw RuntimeException("Circular dependency detected in task breakdown")
        }
        taskIds.addAll(nextTasks.keys)
        nextTasks.keys.forEach { taskMap.remove(it) }
      }
      return taskIds
    }
  }
}