package io.mellster2012.taskservice.impl

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.lightbend.lagom.scaladsl.testkit.PersistentEntityTestDriver
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.testkit.PersistentEntityTestDriver.Reply
import io.mellster2012.taskservice.api.Task
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class TaskEntitySpec extends WordSpec with Matchers with BeforeAndAfterAll {

  private val system = ActorSystem("TaskEntitySpec",
    JsonSerializerRegistry.actorSystemSetupFor(TaskSerializerRegistry))

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TaskEntitySpec._

  val testDriver = new PersistentEntityTestDriver(system, new TaskEntity, taskId.toString)

  val taskTestDriver = new PersistentEntityTestDriver(system, new TaskEntity, anotherTaskId.toString)

  private def withTestDriver(block: PersistentEntityTestDriver[TaskCommand[_], TaskEvent, TaskState] => Unit): Unit = {
    val driver = new PersistentEntityTestDriver(system, new TaskEntity, taskId.toString)
    block(driver)
    driver.getAllIssues should have size 0
  }

  private def withTestDriver2(block: PersistentEntityTestDriver[TaskCommand[_], TaskEvent, TaskState] => Unit): Unit = {
    val driver = new PersistentEntityTestDriver(system, new TaskEntity, anotherTaskId.toString)
    block(driver)
    driver.getAllIssues should have size 0
  }

  "Task entity" should {

    "should not create a task without a name" in {
      val outcome = testDriver.run(CreateTask(name = "", reporter = "reporter", assignee = "assignee", description = "description"))
      outcome.sideEffects.head match {
        case Reply(_: RuntimeException) => assert(true)
        case _ => assert(false, "Should have failed")
      }
      testDriver.getAllIssues should have size 1
    }

    "create a new task" in {
      val outcome = testDriver.run(CreateTask(name = task.name, description = task.description, reporter = task.reporter, assignee = task.assignee))
      val ev = outcome.events.head.asInstanceOf[TaskCreated]
      println("Created: " + ev.task)
      assert(ev.task.created.isDefined)
      assert(ev.task.updated.isDefined)
      assert(ev.task == task.copy(created = ev.task.created, updated = ev.task.updated))
    }

    "update a task" in {
      val outcome = testDriver.run(UpdateTask(name = taskUpdate.name, description = taskUpdate.description, reporter = taskUpdate.reporter, assignee = taskUpdate.assignee, status = taskUpdate.status))
      val ev = outcome.events.head.asInstanceOf[TaskUpdated]
      println("Updated: " + ev.task)
      assert(ev.task.created.isDefined)
      assert(ev.task.updated.isDefined)
      assert(ev.task == taskUpdate.copy(created = ev.task.created, updated = ev.task.updated))
    }

    "get a task" in {
      val outcome = testDriver.run(GetTask(taskId))
      val task = outcome.replies.head.asInstanceOf[Task]
      println("Get: " + task)
      assert(task == taskUpdate.copy(created = task.created, updated = task.updated))
    }
  }
}

object TaskEntitySpec {
  val taskId = UUID.randomUUID
  val anotherTaskId = UUID.randomUUID

  lazy val task = Task(
    id = taskId,
    name = "name",
    reporter = "reporter",
    assignee = "assignee",
    description = "description",
  )

  lazy val taskUpdate = Task(
    id = taskId,
    name = "anothername",
    reporter = "anotherreporter",
    assignee = "anotherassignee",
    description = "anotherdescription"
  )
}
