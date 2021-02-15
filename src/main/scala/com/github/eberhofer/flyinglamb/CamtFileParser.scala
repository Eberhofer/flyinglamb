package com.github.eberhofer.flyinglamb

import java.time.LocalDateTime
import java.util.UUID

import slick.jdbc.PostgresProfile.api._
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import DatabaseHandler.StoreCamtFileData

import scala.xml.{Elem, XML}

object CamtFileParser {
  def apply(filePath: String, fileName: String, databaseHandler: ActorRef[DatabaseHandler.DatabaseHandlerRequest]): Behavior[CamtFileParser.CamtFileParserRequest] = {
    Behaviors.setup[CamtFileParser.CamtFileParserRequest](context => new CamtFileParser(context, filePath, fileName, databaseHandler))
  }

  sealed trait CamtFileParserRequest
  case object WriteCamtFile extends CamtFileParserRequest
  case object CamtFileParserStop extends CamtFileParserRequest
}

class CamtFileParser(context: ActorContext[CamtFileParser.CamtFileParserRequest], filePath: String, fileName: String, databaseHandler: ActorRef[DatabaseHandler.DatabaseHandlerRequest])
  extends AbstractBehavior[CamtFileParser.CamtFileParserRequest](context) {
  import CamtFileParser._
  context.log.info(s"CamtFileParser started ${fileName}")

  val xml: Elem = XML.loadFile(filePath)
  val isV4: Boolean = xml.attributes.toString.contains("camt.053.001.04") // TODO implement proper version handling
  val iban: String = (xml \ "BkToCstmrStmt" \ "Stmt" \ "Acct" \ "Id" \ "IBAN").text
  val balances: Seq[(String, String, BigDecimal, LocalDateTime)]= (xml \ "BkToCstmrStmt" \ "Stmt" \ "Bal")
    .map{ y =>
      (
        (y \ "Tp" \ "CdOrPrtry" \ "Cd").text,
        (y \ "Amt" \ "@Ccy").text,
        BigDecimal((y \ "Amt").text) * (if((y \ "CdtDbtInd").text=="CRDT") 1 else -1),
        LocalDateTime.parse((y \ "Dt" \ "Dt").text + "T00:00:00")
      )
    }
  val (fromDate, toDate) = if(isV4) {
    (LocalDateTime.parse((xml \ "BkToCstmrStmt" \ "Stmt" \ "FrToDt" \ "FrDtTm").text),LocalDateTime.parse((xml \ "BkToCstmrStmt" \ "Stmt" \ "FrToDt" \ "ToDtTm").text))
  } else {
    (balances.filter(t => t._1 == "OPBD").head._4, balances.filter(t => t._1 == "CLBD").head._4)
  }
  val camtFile: CamtFile = CamtFile(
    id = Some(UUID.randomUUID()),
    fileName = fileName,
    statementId = (xml \ "BkToCstmrStmt" \ "Stmt" \ "Id").text,
    messageId = (xml \ "BkToCstmrStmt" \ "GrpHdr" \ "MsgId").text,
    electronicSequenceNumber = (xml \ "BkToCstmrStmt" \ "Stmt" \ "ElctrncSeqNb").text.toInt,
    creationTime = LocalDateTime.parse((xml \ "BkToCstmrStmt" \ "Stmt" \ "CreDtTm").text.take(19)),
    iban = (xml \ "BkToCstmrStmt" \ "Stmt" \ "Acct" \ "Id" \ "IBAN").text,
    currency = balances.filter(t => t._1 == "OPBD").head._2,
    fromDate = fromDate,
    toDate = toDate,
    openBalance = balances.filter(t => t._1 == "OPBD").head._3,
    closeBalance = balances.filter(t => t._1 == "CLBD").head._3
  )

  val camtFileContent: CamtFileContent = CamtFileContent(camtFile.id.get, xml.toString)

  context.log.info(s"iban: ${camtFile.iban}")

  override def onMessage(msg: CamtFileParserRequest): Behavior[CamtFileParserRequest] = msg match {
    case WriteCamtFile =>
      println("Robin got the message")
      println(camtFile.statementId)
      val camtFiles = TableQuery[CamtFileTable]
      val camtFileContents = TableQuery[CamtFileContentTable]
      val camtTransactions = TableQuery[CamtTransactionTable]
      val newCamtTransactions = xml2CamtTransactions()
      context.log.info(s"number of transactions: ${newCamtTransactions.size}")
      databaseHandler ! StoreCamtFileData(camtFile, camtFileContent, newCamtTransactions)
      Behaviors.stopped
    case _ => Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[CamtFileParserRequest]] = {
    case PostStop =>
    context.log.info ("Robin stopped")
    this
  }


  private def xml2CamtTransactions() = (xml \ "BkToCstmrStmt" \ "Stmt" \ "Ntry")
    .map(y =>
      CamtTransaction(
        Some(UUID.randomUUID()),
        camtFile.id.get, // TODO: Error Handling
        iban,
        LocalDateTime.parse((y \ "BookgDt" \ "Dt").text + "T00:00:00"),
        LocalDateTime.parse((y \ "ValDt" \ "Dt").text + "T00:00:00"),
        (y \ "RvslInd").text == "true",
        (y \ "Amt" \ "@Ccy").text,
        BigDecimal((y \ "Amt").text) * (if ((y \ "CdtDbtInd").text == "CRDT") 1 else -1),
        (y \ "AddtlNtryInf").text,
        (y \ "AcctSvcrRef").text,
        (y \ "NtryDtls" \ "TxDtls" \ "Refs").map(node => Elem(node.prefix, node.label, node.attributes, node.scope, true, node.child: _*)) match {
            case Nil => ""
            case l => l.head.toString
        },
        (y \ "BkTxCd").map(node => Elem(node.prefix, node.label, node.attributes, node.scope, true, node.child: _*)).head.toString
      )
    )
}

