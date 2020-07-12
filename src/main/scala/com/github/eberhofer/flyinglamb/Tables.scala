package com.github.eberhofer.flyinglamb

import java.time.LocalDateTime
import java.util.UUID

import MyPostgresProfile.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape}


class CamtFileTable(tag: Tag)
  extends Table[CamtFile](tag, "camt_file") {

  def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey, O.Default(UUID.randomUUID()))
  def fileName: Rep[String] = column[String]("file_name")
  def statementId: Rep[String] = column[String]("statement_id")
  def messageId: Rep[String] = column[String]("message_id")
  def electronicSequenceNumber: Rep[Int] = column[Int]("electronic_sequence_number")
  def creationTime: Rep[LocalDateTime] = column[LocalDateTime]("creation_time")
  def iban: Rep[String] = column[String]("iban")
  def currency: Rep[String] = column[String]("currency")
  def fromDate: Rep[LocalDateTime] = column[LocalDateTime]("from_date")
  def toDate: Rep[LocalDateTime] = column[LocalDateTime]("to_date")
  def openBalance: Rep[BigDecimal] = column[BigDecimal]("open_balance")
  def closeBalance: Rep[BigDecimal] = column[BigDecimal]("close_balance")

  def * : ProvenShape[CamtFile] =
    (id.?, fileName, statementId, messageId, electronicSequenceNumber, creationTime, iban, currency, fromDate, toDate, openBalance, closeBalance)  <> (CamtFile.tupled, CamtFile.unapply)

}

class CamtFileContentTable(tag: Tag)
extends Table[CamtFileContent](tag, "camt_file_content") {
  def camtFileId: Rep[UUID] = column[UUID]("camt_file_id")
  def camtFileContent: Rep[String] = column[String]("camt_file_content")

  def * : ProvenShape[CamtFileContent] =
    (camtFileId, camtFileContent)  <> (CamtFileContent.tupled, CamtFileContent.unapply)

}


class CamtTransactionTable(tag: Tag)
extends Table[CamtTransaction](tag, "camt_transaction") {
  def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey, O.Default(UUID.randomUUID()))
  def camtFileId: Rep[UUID] = column[UUID]("camt_file_id")
  def iban: Rep[String] = column[String]("iban")
  def bookingDate: Rep[LocalDateTime] = column[LocalDateTime]("booking_date")
  def valueDate: Rep[LocalDateTime] = column[LocalDateTime]("value_date")
  def isReversal: Rep[Boolean] = column[Boolean]("is_reversal")
  def currency: Rep[String] = column[String]("currency")
  def amount: Rep[BigDecimal] = column[BigDecimal]("amount")
  def additionalInfo: Rep[String] = column[String]("additional_info")
  def accountServicerReference: Rep[String] = column[String]("account_servicer_reference")
  def transactionReferences: Rep[String] = column[String]("transaction_references")
  def bankTransactionCode: Rep[String] = column[String]("bank_transaction_code")

  // A reified foreign key relation that can be navigated to create a join
  def camtFile: ForeignKeyQuery[CamtFileTable, CamtFile] =
    foreignKey("fk_camt_transaction_camt_file_id", camtFileId, TableQuery[CamtFileTable])(_.id)

  def * : ProvenShape[CamtTransaction] =
    (id.?, camtFileId, iban, bookingDate, valueDate, isReversal, currency, amount, additionalInfo, accountServicerReference, transactionReferences, bankTransactionCode) <> (CamtTransaction.tupled, CamtTransaction.unapply)
}
