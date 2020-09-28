package io.mellster2012.taskservice.impl

import java.time.Instant
import java.util.UUID

import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import io.mellster2012.taskservice.api.{Status, Task}
import play.api.libs.json.{Format, Json}

class TaskEntity extends PersistentEntity {

  override type Command = TaskCommand[_]
  override type Event = TaskEvent
  override type State = TaskState

  override def initialState: TaskState = TaskState(Task(id = UUID.fromString(entityId), name = "", description = "", reporter = "", assignee = "", created = None, updated = None))

  def updateTask(state: TaskState, name: String, description: String, reporter: String, assignee: String, status: Status, created: Option[Instant] = None, updated: Option[Instant] = None): Task = state.task.copy(
    name = name,
    description = description,
    reporter = reporter,
    assignee = assignee,
    status = status,
    created = created,
    updated = updated
  )

  override def behavior: Behavior = {
    case TaskState(task, created, deleted) => Actions().onCommand[CreateTask, TaskCreated] {
      case (CreateTask(name, description, reporter, assignee), ctx, state) => {
        if (name.length > 128 || !name.matches("[A-Za-z0-9]+")) {
          ctx.commandFailed(new RuntimeException("Task name must be alphanumeric and between 1-128 characters long"))
          ctx.done
        }  else if (reporter.length > 64 || assignee.length > 64 || !reporter.matches("[A-Za-z]+") || ! assignee.matches("[A-Za-z]+")) {
          ctx.commandFailed(new RuntimeException("Reporter and Assignee names must be alphabetic (letters only) and between 1-64 characters long"))
          ctx.done
        } else if (created) {
          ctx.commandFailed(new RuntimeException("Task exists"))
          ctx.done
        } else {
          val now = Instant.now()
          ctx.thenPersist(TaskCreated(updateTask(state, name, description, reporter, assignee, Status.Assigned, Some(now), Some(now))))(ctx.reply)
        }
      }
    }.onCommand[UpdateTask, TaskUpdated] {
      case (UpdateTask(name, description, reporter, assignee, status), ctx, state) => {
        if (name.length > 128 || !name.matches("[A-Za-z0-9]+")) {
          ctx.commandFailed(new RuntimeException("Task name must be alphanumeric and between 1-128 characters long"))
          ctx.done
        }  else if (reporter.length > 64 || assignee.length > 64 || !reporter.matches("[A-Za-z]+") || ! assignee.matches("[A-Za-z]+")) {
          ctx.commandFailed(new RuntimeException("Reporter and Assignee names must be alphabetic (letters only) and between 1-64 characters long"))
          ctx.done
        } else if (deleted) {
          ctx.commandFailed(new RuntimeException("Can't update a deleted task (please create a new one)"))
          ctx.done
        } else {
          ctx.thenPersist(TaskUpdated(updateTask(state, name, description, reporter, assignee, status, state.task.created, Some(Instant.now))))(ctx.reply)
        }
      }
    }.onCommand[DeleteTask, TaskDeleted] {
    case (DeleteTask(id), ctx, _) => ctx.thenPersist(TaskDeleted(id))(ctx.reply)
    }.onReadOnlyCommand[GetTask, Task] {
      case (GetTask(_), ctx, _) => ctx.reply(task)
    }.onEvent {
      case (TaskCreated(task), state) => state.copy(task = task, true, false)
      case (TaskUpdated(task), state) => state.copy(task = task)
      case (TaskDeleted(_), state) => state.copy(deleted = true)
    }
  }
}

case class TaskState(task: Task, created: Boolean = false, deleted: Boolean = false)
object TaskState {
  implicit val format: Format[TaskState] = Json.format
}

sealed trait TaskEvent extends AggregateEvent[TaskEvent] {
  def aggregateTag: AggregateEventTag[TaskEvent] = TaskEvent.Tag
}

object TaskEvent {
  val Tag: AggregateEventTag[TaskEvent] = AggregateEventTag[TaskEvent]
}

case class TaskCreated(task: Task) extends TaskEvent
object TaskCreated { implicit val format: Format[TaskCreated] = Json.format }

case class TaskUpdated(task: Task) extends TaskEvent
object TaskUpdated { implicit val format: Format[TaskUpdated] = Json.format }

case class TaskDeleted(id: UUID) extends TaskEvent
object TaskDeleted { implicit val format: Format[TaskDeleted] = Json.format }

sealed trait TaskCommand[R] extends ReplyType[R]

case class GetTask(id: UUID) extends TaskCommand[Task]
object GetTask { implicit val format: Format[GetTask] = Json.format }

case class GetTasks(reporter: Option[String], assignee: Option[String], status: Option[Status]) extends TaskCommand[Seq[Task]]
object GetTasks { implicit val format: Format[GetTasks] = Json.format }

case class CreateTask(name: String, description: String, reporter: String, assignee: String) extends TaskCommand[TaskCreated]
object CreateTask { implicit val format: Format[CreateTask] = Json.format }

case class UpdateTask(name: String, description: String, reporter: String, assignee: String, status: Status) extends TaskCommand[TaskUpdated]
object UpdateTask { implicit val format: Format[UpdateTask] = Json.format }

case class DeleteTask(id: UUID) extends TaskCommand[TaskDeleted]
object DeleteTask { implicit val format: Format[DeleteTask] = Json.format }

/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object TaskSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: scala.collection.immutable.Seq[JsonSerializer[_]] = scala.collection.immutable.Seq(
    JsonSerializer[Task],
    JsonSerializer[TaskState],
    JsonSerializer[GetTask],
    JsonSerializer[CreateTask],
    JsonSerializer[UpdateTask],
    JsonSerializer[DeleteTask],
    JsonSerializer[TaskCreated],
    JsonSerializer[TaskUpdated],
    JsonSerializer[TaskDeleted],
    JsonSerializer[Seq[Task]]
  )
}
