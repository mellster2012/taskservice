package io.mellster2012.taskservice.impl

import java.util.Date
import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}

import scala.concurrent.{ExecutionContext, Future}

class TaskEventProcessor(cassandraSession: CassandraSession, executionContext: ExecutionContext) extends ReadSideProcessor[TaskEvent] {
  override def buildHandler(): ReadSideProcessor.ReadSideHandler[TaskEvent] = {
    new ReadSideHandler[TaskEvent] {
      override def globalPrepare(): Future[Done] =
        createTables(executionContext)

      override def handle(): Flow[EventStreamElement[TaskEvent], Done, NotUsed] = {
        Flow[EventStreamElement[TaskEvent]].mapAsync(4) { eventElement =>
          eventElement.event match {
            case create: TaskCreated => process(create)
            case update: TaskUpdated => process(update)
            case delete: TaskDeleted => process(delete)
          }
        }
      }
    }
  }

  def process(created: TaskCreated): Future[Done] = {
    val stmt = "INSERT INTO task (id, name, description, reporter, assignee, status, created, updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    cassandraSession.executeWrite(stmt, created.task.id, created.task.name, created.task.description, created.task.reporter, created.task.assignee, created.task.status.name, new Date(created.task.created.get.toEpochMilli), new Date(created.task.updated.get.toEpochMilli))
  }

  def process(updated: TaskUpdated): Future[Done] = {
    val stmt = "UPDATE task SET name = ?, description = ?, reporter = ?, assignee = ?, status = ?, updated = ? WHERE id = ?"
    cassandraSession.executeWrite(stmt, updated.task.name, updated.task.description, updated.task.reporter, updated.task.status.name, updated.task.assignee, new Date(updated.task.updated.get.toEpochMilli), updated.task.id)
  }

  def process(deleted: TaskDeleted): Future[Done] = {
    val stmt = "DELETE FROM task WHERE id = ?"
    cassandraSession.executeWrite(stmt, deleted.id)
  }

  override def aggregateTags: Set[AggregateEventTag[TaskEvent]] = {
    Set(TaskEvent.Tag)
  }

  /**
   * Create the tables and indexes needed for this read side if not already created.
   */
  def createTables(implicit executionContext: ExecutionContext): Future[Done] = {
    cassandraSession.executeCreateTable(
      "CREATE TABLE IF NOT EXISTS task (" +
        "id UUID, " +
        "name text, " +
        "description text, " +
        "reporter text, " +
        "assignee text, " +
        "status text, " +
        "created timestamp, " +
        "updated timestamp, " +
        "PRIMARY KEY (id) " +
        ")"
    )
      .flatMap(_ => cassandraSession.executeWrite("CREATE INDEX IF NOT EXISTS idx_status ON taskservice.task(status)")) // OK due to low cardinality
      .flatMap(_ => cassandraSession.executeWrite("CREATE INDEX IF NOT EXISTS idx_reporter ON taskservice.task(reporter)")) //TODO: use SASI (CONTAINS or PREFIX) once supported in prod
      .flatMap(_ => cassandraSession.executeWrite("CREATE INDEX IF NOT EXISTS idx_assignee ON taskservice.task(assignee)")) // TODO: even better use SAI with paid Datastax enterprise
  }
}