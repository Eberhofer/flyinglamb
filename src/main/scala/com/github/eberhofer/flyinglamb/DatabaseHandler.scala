package com.github.eberhofer.flyinglamb

import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.postgresql.util.PSQLException
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object DatabaseHandler {
  def apply(dbPath: String): Behavior[DatabaseHandlerRequest] = Behaviors.setup[DatabaseHandlerRequest](context => new DatabaseHandler(context, dbPath))

  sealed trait DatabaseHandlerRequest
  case class StoreCamtFileData(camtFile: CamtFile, camtFileContent: CamtFileContent, camtTransactions: Seq[CamtTransaction]) extends DatabaseHandlerRequest
  case object Harakiri extends DatabaseHandlerRequest
  case class GetCamtTransactions(sender: ActorRef[GotCamtTransactions]) extends DatabaseHandlerRequest
  case class GotCamtTransactions(transactions: Seq[CamtTransaction])
}

class DatabaseHandler(context: ActorContext[DatabaseHandler.DatabaseHandlerRequest], dbPath: String) extends AbstractBehavior[DatabaseHandler.DatabaseHandlerRequest](context) {
  import DatabaseHandler._

  context.log.info(s"Sanji started with dbPath $dbPath")
  val db = Database.forConfig(dbPath)

  override def onMessage(msg: DatabaseHandlerRequest): Behavior[DatabaseHandlerRequest] = msg match {
    case StoreCamtFileData(camtFile, camtFileContent, newCamtTransactions) =>
      context.log.info(s"Received the message for CamtFile ${camtFile.statementId}")
      val camtFiles = TableQuery[CamtFileTable]
      val camtFileContents = TableQuery[CamtFileContentTable]
      val camtTransactions = TableQuery[CamtTransactionTable]
      val insertActions = DBIO.seq(camtFiles += camtFile, camtFileContents += camtFileContent, camtTransactions ++= newCamtTransactions)
      try {
        Await.result(db.run(insertActions.transactionally), Duration.apply(20, TimeUnit.SECONDS))
      }
      catch {
        case e: PSQLException => context.log.info(s"Encountered error ${e.getServerErrorMessage} for file ${camtFile.statementId}")
      }
      finally {
        //TODO
      }
      this
 /*   case GetCamtTransactions(sender) =>
      context.log.info(s"Received message GetTransactions")
      sender ! GotCamtTransactions(db.run(TableQuery[CamtTransactionTable]))
      this*/
    case Harakiri =>
      Behaviors.stopped
    case _ => Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[DatabaseHandlerRequest]] = {
    case PostStop =>
      db.close()
      context.log.info("DatabaseHandler stopped")
      this
  }
}


