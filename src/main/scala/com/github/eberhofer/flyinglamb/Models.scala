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
case class CamtFiles(camtFiles: Seq[CamtFile])

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
                          ) extends Model {
  def smallCamtTransaction: SmallCamtTransaction = SmallCamtTransaction(id, iban, bookingDate, valueDate, currency, amount, additionalInfo)
}

case class CamtTransactions(camtTransactions: Seq[CamtTransaction])

case class SmallCamtTransaction(
                                 id: Option[UUID],
                                 iban: String,
                                 bookingDate: LocalDateTime,
                                 valueDate: LocalDateTime,
                                 currency: String,
                                 amount: BigDecimal,
                                 additionalInfo: String
                               )

case class SmallCamtTransactions(smallCamtTransactions: Seq[SmallCamtTransaction])

case class StockTransaction(
                             id: Option[UUID],
                             name: String
                           )
case class StockTransactions(stockTransactions: Seq[StockTransaction])

case class UserCredential(
                       id: Option[UUID],
                       email: String,
                       password: String
               ) {

}

case class UserCredentials(credentials: Seq[UserCredential])

case class AuthToken(
                       id: Option[UUID],
                       credentialId: UUID,
                       tokenUserName: UUID,
                       tokenSecret: UUID,
                       valid_until: LocalDateTime,
                       is_logged_out: Boolean = false
                     ) {
  def auth2BearerToken: String = tokenUserName.toString + ':' + tokenSecret.toString
}

case class AuthTokens(authTokens: Seq[AuthToken])
