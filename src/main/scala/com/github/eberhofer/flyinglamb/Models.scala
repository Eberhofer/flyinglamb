package com.github.eberhofer.flyinglamb

import java.util.UUID
import java.time.LocalDateTime

sealed trait Model
case class CamtFile(
                     id: Option[UUID],
                     fileName: String,
                     statementId: String,
                     messageId: String,
                     electronicSequenceNumber: Int,
                     creationTime: LocalDateTime,
                     iban: String,
                     currency: String,
                     fromDate: LocalDateTime,
                     toDate: LocalDateTime,
                     openBalance: BigDecimal,
                     closeBalance: BigDecimal,
                   ) extends Model

case class CamtFileContent(
                          camtFileId: UUID,
                          camtFileContent: String //TODO: Elem
                          ) extends Model

case class CamtTransaction(
                          id: Option[UUID],
                          camtFileId: UUID,
                          iban: String, //TODO: redundant, but can be optimized later
                          bookingDate: LocalDateTime,
                          valueDate: LocalDateTime,
                          isReversal: Boolean = false,
                          currency: String,
                          amount: BigDecimal,
                          additionalInfo: String,
                          accountServicerReference: String,
                          transactionReferences: String,  // TODO: Elem,
                          bankTransactionCode: String // TODO:  Elem
                          ) extends Model

