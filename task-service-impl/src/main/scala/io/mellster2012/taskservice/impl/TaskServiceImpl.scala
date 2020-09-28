package io.mellster2012.taskservice.impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.datastax.driver.core.PreparedStatement

import scala.concurrent.ExecutionContext.Implicits.global
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import java.time.Instant
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future, Promise}
import akka.actor.ActorSystem
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.{Done, NotUsed}
import com.datastax.driver.core.{Row, SimpleStatement}
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession
import com.lightbend.lagom.scaladsl.persistence.ReadSide
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import io.mellster2012.taskservice.api
import io.mellster2012.taskservice.api.{Event, NewTask, Status, Task, TaskService}
import play.api.Mode

class TaskServiceImpl(persistentEntityRegistry: PersistentEntityRegistry, readSide: ReadSide, system: ActorSystem, context: LagomApplicationContext, cassandraSession: CassandraSession, executionContext: ExecutionContext) extends TaskService {

  readSide.register[TaskEvent](new TaskEventProcessor(cassandraSession, executionContext))
  // Akka Management hosts the HTTP routes for debugging
  AkkaManagement.get(system).start()
  if (context.playContext.environment.mode != Mode.Dev) {
    // Starting the bootstrap process in production
    ClusterBootstrap.get(system).start()
  }

  override def getTask(id: UUID) = ServiceCall { _ =>
    val ref = persistentEntityRegistry.refFor[TaskEntity](id.toString)
    ref.ask(GetTask(id)).map(t => Task(t.id, t.name, t.description, t.reporter, t.assignee, t.status, t.created, t.updated))
  }

  override def getTasks(reporter: String, assignee: String, status: String): ServiceCall[NotUsed, Seq[Task]] = ServiceCall { _ => //if (reporter.is == null && assignee == None && status == None) throw new RuntimeException("Query needs at least 1 filter (reporter, assignee, status)")
    val cql = new StringBuilder("SELECT * FROM taskservice.task where ")
    if (! reporter.isEmpty) cql ++= s"reporter = '${reporter}'"
    if (! reporter.isEmpty && !assignee.isEmpty) cql ++= s" AND assignee = '${assignee}'"
    else if (!assignee.isEmpty) cql ++= s"assignee = '${assignee}'"
    if ((!reporter.isEmpty || !assignee.isEmpty) && !status.isEmpty) cql ++= s" AND status = '${status}'"
    else if (!status.isEmpty) cql ++= s"status = '${status}'"
    val q = cql ++= " ALLOW FILTERING" toString
    val tasks: scala.concurrent.Future[Seq[Task]] = cassandraSession.selectAll(new SimpleStatement(q)).map(rows => rows.map(row => toTask(row)))
    tasks
  }

  implicit def toTask(row: Row): Task = {
    new Task(row.getUUID("id"), row.getString("name"), row.getString("description"),
      row.getString("reporter"), row.getString("assignee"), Status.fromName(row.getString("status")),
      Some(row.getTimestamp("created").toInstant), Some(row.getTimestamp("updated").toInstant))
  }

  override def createTask= ServiceCall { task: NewTask =>
    val id = UUID.randomUUID
    val ref = persistentEntityRegistry.refFor[TaskEntity](id.toString)
    ref.ask(CreateTask(name = task.name, description = task.description, reporter = task.reporter, assignee = task.assignee)).map(x => x.task)
  }

  override def updateTask(id: UUID) = ServiceCall{ task: api.UpdateTask =>
    val ref = persistentEntityRegistry.refFor[TaskEntity](id.toString)
    ref.ask(UpdateTask(name = task.name, description = task.description, reporter = task.reporter, assignee = task.assignee, status = Status.fromName(task.status))).map(_ => Done)
  }

  override def deleteTask(id: UUID) = ServiceCall{ _ =>
    val ref = persistentEntityRegistry.refFor[TaskEntity](id.toString)
    ref.ask(DeleteTask(id)).map(_ => Done)
  }

  override def tasksTopic(): Topic[Event] =
    TopicProducer.singleStreamWithOffset {
      fromOffset =>
        persistentEntityRegistry.eventStream(TaskEvent.Tag, fromOffset)
          .map(ev => (ev, ev.offset))
    }

  implicit private def toEvent(ev: EventStreamElement[TaskEvent]): Event = {
    ev.event match {
      case TaskUpdated(t) => api.TaskUpdated(t.id, t)
      case TaskCreated(t) => api.TaskCreated(t.id, t)
      case TaskDeleted(id) => api.TaskDeleted(id)
    }
  }
}
