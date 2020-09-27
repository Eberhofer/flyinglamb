package com.github.eberhofer.flyinglamb

import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import java.util.UUID

import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration


class CamtTransactionRepository() {
  val db = Database.forConfig("flyinglamb")

  val tableQuery = TableQuery[CamtTransactionTable]

  def find(id: UUID): Future[Option[CamtTransaction]] = {
    db.run(tableQuery.filter(f => f.id === id).take(1).result.headOption)
  }

  def getCamtTransactions(iban: Option[String], startValueLocalDateTime: LocalDateTime, endValueLocalDateTime: LocalDateTime) = {
    val camtTransactions = for {
      ct <- tableQuery
      if ct.iban === (if (iban.isEmpty) ct.iban else iban.get)
      if ct.valueDate >= startValueLocalDateTime && ct.valueDate <= endValueLocalDateTime
    } yield ct
    val camtTransactionsAction: DBIO[Seq[CamtTransaction]] = camtTransactions.result
    db.run(camtTransactionsAction)
  }

  def getSmallCamtTransactions(iban: Option[String], startValueLocalDateTime: LocalDateTime, endValueLocalDateTime: LocalDateTime) = {
    val camtTransactions = for {
      ct <- TableQuery[CamtTransactionTable]
      if ct.iban === (if (iban.isEmpty) ct.iban else iban.get)
      if ct.valueDate >= startValueLocalDateTime && ct.valueDate <= endValueLocalDateTime
    } yield ct
    val camtTransactionsAction: DBIO[Seq[CamtTransaction]] = camtTransactions.result
    db.run(camtTransactionsAction).map(f=>f.map(_.smallCamtTransaction))
  }



}
