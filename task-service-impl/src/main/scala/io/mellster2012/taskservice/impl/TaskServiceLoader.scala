package io.mellster2012.taskservice.impl

import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.client.ConfigurationServiceLocatorComponents
import com.softwaremill.macwire._
import io.mellster2012.taskservice.api.TaskService

class TaskServiceLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new ServiceManagerApplication(context) with ConfigurationServiceLocatorComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new ServiceManagerApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[TaskService])
}

abstract class ServiceManagerApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  // Bind service
  override lazy val lagomServer = serverFor[TaskService](wire[TaskServiceImpl])

  // Register JSON serializers
  override lazy val jsonSerializerRegistry = TaskSerializerRegistry

  // Register Task persistent entity
  persistentEntityRegistry.register(wire[TaskEntity])
}
