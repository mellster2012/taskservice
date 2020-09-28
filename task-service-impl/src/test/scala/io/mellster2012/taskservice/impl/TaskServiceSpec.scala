package io.mellster2012.taskservice.impl

import akka.Done
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import io.mellster2012.taskservice.api.{NewTask, TaskService, UpdateTask}
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers}

// TODO: Doesn't work since keyspace and tables are not created for spec tests
class TaskServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  private val server = ServiceTest.startServer(
    ServiceTest.defaultSetup
      .withCassandra()
  ) { ctx =>
    new ServiceManagerApplication(ctx) with LocalServiceLocator
  }

  val client = server.serviceClient.implement[TaskService]

  override protected def afterAll() = server.stop()
  import TaskEntitySpec._


  "TaskService" should {

    "create a task" in {
      client.createTask.invoke(NewTask(
        name = task.name, reporter = task.reporter, assignee = task.assignee, description = task.description
      )).map{ answer =>
        answer should === (task.copy(id = answer.id, created = answer.created, updated = answer.created))
      }
    }

    "update a task" in {
      client.updateTask(taskId).invoke(io.mellster2012.taskservice.api.UpdateTask(
        name = taskUpdate.name, reporter = taskUpdate.reporter, assignee = taskUpdate.assignee, description = taskUpdate.description, status = taskUpdate.status.name
      )).map{ answer =>
        answer shouldBe Done
      }
    }

    "get tasks by reporter/assignee/status" in {
      client.createTask.invoke(NewTask(
        name = task.name, reporter = task.reporter, assignee = task.assignee, description = task.description
      )).map{ answer =>
        answer should === (task.copy(id = answer.id, created = answer.created, updated = answer.created))
      }
      client.getTasks("reporter", "", "").invoke.map{ answer =>
        println(answer)
        answer.length shouldBe 1
      }
    }
  }
}
