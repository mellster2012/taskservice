package io.mellster2012.taskservice.api

import java.time.Instant

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Json, OFormat, Reads, Writes}
import java.util.UUID

import com.lightbend.lagom.scaladsl.api.transport.Method

trait TaskService extends Service {

  def getTask(id: UUID): ServiceCall[NotUsed, Task]
  // TODO: Use value classes once the bugs like https://github.com/lagom/lagom/issues/585 are resolved
  def getTasks(reporter: String, assignee: String, status: String): ServiceCall[NotUsed, Seq[Task]]
  def createTask: ServiceCall[NewTask, Task]
  def updateTask(id: UUID): ServiceCall[UpdateTask, Done]
  def deleteTask(id: UUID): ServiceCall[NotUsed, Done]

  def tasksTopic(): Topic[Event]

  override final def descriptor = {
    import Service._

    named("tasks")
      .withCalls(
        restCall(Method.POST, "/task", createTask _), // TODO: Return 201 instead of 200
        restCall(Method.PUT, "/task/:id", updateTask _), // TODO: Return 204 instead of 200
        restCall(Method.DELETE, "/task/:id", deleteTask _), // TODO: Return 204 instead of 200
        restCall(Method.GET, "/task/:id", getTask _),
        pathCall("/task?reporter&assignee&status", getTasks _)
      )
      .withTopics(
        topic("tasks", tasksTopic)
          // Kafka partitions messages, messages within the same partition will
          // be delivered in order, to ensure that all messages for the same user
          // go to the same partition (and hence are delivered in order with respect
          // to that user), we configure a partition key strategy that extracts the
          // name as the partition key.
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[Event](_.id.toString)
          )
      )
      .withAutoAcl(true) // TODO: use Kafka
  }
}

sealed abstract class Status extends Product with Serializable { def name: String }
object Status {
  final case object Assigned extends Status { val name = "Assigned" }
  final case object Completed extends Status { val name = "Completed" }
  implicit val reads: Reads[Status] = Reads {
    case JsString("Assigned") => JsSuccess(Assigned)
    case JsString("Completed") => JsSuccess(Completed)
    case _ => JsError("Status parse error")
  }

  implicit val writes: Writes[Status] = Writes { status =>
    JsString(status.name)
  }

  def fromName(name: String): Status = {
    if (Assigned.name == name) Assigned else if (Completed.name == name) Completed else throw new RuntimeException(s"Invalid status: $name")
  }
}

case class Task(id: UUID, name: String, description: String, reporter: String, assignee: String, status: Status = Status.Assigned, created: Option[Instant] = None, updated: Option[Instant] = None)
object Task { implicit val format: Format[Task] = Json.format }

case class NewTask(name: String, description: String, reporter: String, assignee: String)
object NewTask { implicit val format: Format[NewTask] = Json.format }

case class UpdateTask(name: String, description: String, reporter: String, assignee: String, status: String)
object UpdateTask { implicit val format: Format[UpdateTask] = Json.format }

// TODO: Use value classes like this once bugs like https://github.com/lagom/lagom/issues/585 are resolved
case class GetTasks(reporter: Option[String], assignee: Option[String], status: Option[Status])
object GetTasks { implicit val format: Format[GetTasks] = Json.format }

sealed trait Event {
  def id: UUID
}
object Event { implicit val format: OFormat[Event] = Json.format[Event] }

case class TaskUpdated(id: UUID, task: Task) extends Event
object TaskUpdated { implicit val format: Format[TaskUpdated] = Json.format[TaskUpdated] }

case class TaskCreated(id: UUID, task: Task) extends Event
object TaskCreated { implicit val format: Format[TaskCreated] = Json.format[TaskCreated] }

case class TaskDeleted(id: UUID) extends Event
object TaskDeleted { implicit val format: Format[TaskDeleted] = Json.format[TaskDeleted] }
