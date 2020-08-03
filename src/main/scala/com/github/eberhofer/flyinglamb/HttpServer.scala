package com.github.eberhofer.flyinglamb

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn
import org.postgresql.util.PSQLException
import slick.jdbc.PostgresProfile.api._
import com.github.t3hnar.bcrypt._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import spray.json._

import scala.util.{Failure, Success}

object CamtTransactionJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)
    def read(value: JsValue) = {
      value match {
        case JsString(uuid) => UUID.fromString(uuid)
        case _              => throw DeserializationException("Expected hexadecimal UUID string")
      }
    }
  }

  implicit object LocalDateTimeFormat extends JsonFormat[LocalDateTime] {
    def write(localDateTime: LocalDateTime) = JsString(localDateTime.toString)
    def read(value: JsValue) = {
      value match {
        case JsString(localDateTime) => LocalDateTime.parse(localDateTime)
        case _              => throw DeserializationException("Expected LocalDateTime string")
      }
    }
  }

  implicit val camtTransactionFormat = jsonFormat12(CamtTransaction)
  implicit val camtTransactionsFormat = jsonFormat1(CamtTransactions)
  implicit val smallCamtTransactionFormat = jsonFormat7(SmallCamtTransaction)
  implicit val smallCamtTransactionsFormat = jsonFormat1(SmallCamtTransactions)
  implicit val credentialFormat = jsonFormat3(Credential)
}


object HttpServer {
  import CamtTransactionJsonProtocol._

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    //implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher


    val db = Database.forConfig("flyinglamb")
    def createCredential(credential: Credential): Future[Credential] = {
      val credentials = TableQuery[CredentialTable]
      val secureCredential: Credential = credential.password.bcryptSafeBounded match {
        case Success(pwHash) => credential.copy(id = Some(UUID.randomUUID()), password = pwHash)
        case Failure(error) => throw error
      }
      db.run(credentials returning credentials += secureCredential)
    }

    val port = system.settings.config.getInt("httpServer.port")
    println(s"port $port")

    val route = concat(
      pathEndOrSingleSlash {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>top level</h1>"))
        }
      },
      path("credentials") {
        pathEndOrSingleSlash {
          post {
            decodeRequest {
              entity(as[Credential]) { credential =>
                val credentialsQuery: DBIO[Seq[Credential]] = TableQuery[CredentialTable].filter(c => c.email === credential.email).result
                val credentials: Seq[Credential] = Await.result(
                  db.run(credentialsQuery),
                  Duration.apply(20, TimeUnit.SECONDS)
                )
                credentials match {
                  case h :: Nil => complete(StatusCodes.BadRequest, "Email already exists.")
                  case Nil => complete(StatusCodes.Created, createCredential(credential))
                }
              }
            }
          }
        }
      },
      /* TODO - WIP
      path("auth") {
        pathEndOrSingleSlash {
          post {
            decodeRequest {
              entity(as[Credential]) { credential =>
                val credentialsQuery: DBIO[Seq[Credential]] = TableQuery[CredentialTable].filter(c => c.email === credential.email).result
                val credentialsCandidate: Option[Credential] = Await.result(
                  db.run(credentialsQuery),
                  Duration.apply(20, TimeUnit.SECONDS)
                ).headOption
                credentialsCandidate match {
                  case Some(c) => credential.password.bcryptSafeBounded match {
                    case Success(true) =>
                    case _ => complete(StatusCodes.Unauthorized, "No user matched the credentials.")
                  }
                  case _ => complete(StatusCodes.Unauthorized, "No user matched the credentials.")
                }
              }
            }
          }
        }
      },  */
      path("camt") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      },
      pathPrefix("camttransactions"){
        concat(
          //pathEndOrSingleSlash {
            get {
              parameters(("iban".?, "startvaluedate".?, "endvaluedate".?)) {
                (iban, startValueDate, endValueDate) =>
                  println(s"iban = $iban")
                  val startValueLocalDateTime = LocalDateTime.parse(startValueDate.getOrElse("2019-01-01T00:00"))
                  val endValueLocalDateTime = endValueDate match {
                    case None => LocalDateTime.now()
                    case Some(string) => LocalDateTime.parse(string)
                  }
                  val camtTransactions = for {
                    ct <- TableQuery[CamtTransactionTable]
                    if ct.iban === (if (iban.isEmpty) ct.iban else iban.get)
                    if ct.valueDate >= startValueLocalDateTime && ct.valueDate <= endValueLocalDateTime
                  } yield ct
                val camtTransactionsAction: DBIO[Seq[CamtTransaction]] = camtTransactions.result
                val camtTransactionsFuture = db.run(camtTransactionsAction)
                val camtTransactionList: Seq[CamtTransaction] = Await.result(camtTransactionsFuture, Duration.apply(20, TimeUnit.SECONDS))
                val camtTransactionJson = camtTransactionList.toJson //.map(c => SmallCamtTransaction(c.iban, c.bookingDate, c.valueDate, c.currency, c.amount, c.additionalInfo)).toJson
                complete(HttpEntity(ContentTypes.`application/json`, camtTransactionJson.toString))
              }
            //}
          },
          path("small") {
            get {
              val camtTransactions = TableQuery[CamtTransactionTable]
              val camtTransactionsAction: DBIO[Seq[CamtTransaction]] = camtTransactions.result
              val camtTransactionsFuture = db.run(camtTransactionsAction)
              val camtTransactionList: Seq[CamtTransaction] = Await.result(camtTransactionsFuture, Duration.apply(20, TimeUnit.SECONDS))
              val smallCamtTransactionList = camtTransactionList.map(_.smallCamtTransaction)
              val smallCamtTransactionJson = smallCamtTransactionList.toJson //.map(c => SmallCamtTransaction(c.iban, c.bookingDate, c.valueDate, c.currency, c.amount, c.additionalInfo)).toJson
              complete(HttpEntity(ContentTypes.`application/json`, smallCamtTransactionJson.toString))
            }
          }
        )
      },
      path("processCamtTransactions"){
        get {
          CamtService.apply
          println("done")
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,"<h1>execution of camt service finished</h1>"))
        }
      }
    )



    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}