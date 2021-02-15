package com.github.eberhofer.flyinglamb

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.Base64
import java.nio.charset.StandardCharsets
import java.time.temporal.TemporalUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1}

import scala.io.StdIn
import org.postgresql.util.PSQLException
import slick.jdbc.PostgresProfile.api._
import com.github.t3hnar.bcrypt._

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import spray.json._

import scala.util.{Failure, Success}

object CamtTransactionJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID): JsString = JsString(uuid.toString)
    def read(value: JsValue): UUID = {
      value match {
        case JsString(uuid) => UUID.fromString(uuid)
        case _              => throw DeserializationException("Expected hexadecimal UUID string")
      }
    }
  }

  implicit object LocalDateTimeFormat extends JsonFormat[LocalDateTime] {
    def write(localDateTime: LocalDateTime): JsString = JsString(localDateTime.toString)
    def read(value: JsValue): LocalDateTime = {
      value match {
        case JsString(localDateTime) => LocalDateTime.parse(localDateTime)
        case _              => throw DeserializationException("Expected LocalDateTime string")
      }
    }
  }

  implicit val camtTransactionFormat: RootJsonFormat[CamtTransaction] = jsonFormat12(CamtTransaction)
  implicit val camtTransactionsFormat: RootJsonFormat[CamtTransactions] = jsonFormat1(CamtTransactions)
  implicit val smallCamtTransactionFormat: RootJsonFormat[SmallCamtTransaction] = jsonFormat7(SmallCamtTransaction)
  implicit val smallCamtTransactionsFormat: RootJsonFormat[SmallCamtTransactions] = jsonFormat1(SmallCamtTransactions)
  implicit val credentialFormat: RootJsonFormat[UserCredential] = jsonFormat3(UserCredential)
}

object HttpServer {
  import CamtTransactionJsonProtocol._
  import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

  def main(args: Array[String]) {

    implicit val system: ActorSystem = ActorSystem("my-system")
    //implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    val camtTransactionRepository = new CamtTransactionRepository()

    def passwordHasher(plainTextPassword: String): String = {
      plainTextPassword.bcryptSafeBounded match {
        case Success(pwHash) => pwHash
        case Failure(error) => throw error
      }
    }

    val db = Database.forConfig("flyinglamb")
    def createCredential(credential: UserCredential): Future[UserCredential] = {
      val credentials = TableQuery[CredentialTable]
      val secureCredential: UserCredential = credential.password.bcryptSafeBounded match {
        case Success(pwHash) => credential.copy(id = Some(UUID.randomUUID()), password = pwHash)
        case Failure(error) => throw error
      }
      db.run(credentials returning credentials += secureCredential)
    }

    def createAuthToken(credential: UserCredential): AuthToken = {
      val authTokens = TableQuery[AuthTokenTable]
      val newAuthToken: AuthToken = AuthToken(Some(UUID.randomUUID()), credential.id.get, UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now().plusMinutes(90))
      db.run(authTokens += newAuthToken)
      newAuthToken
    }
    val port = system.settings.config.getInt("httpServer.port")
    println(s"port $port")

    def BasicAuthAuthenticator(credentials: Credentials): Option[UserCredential] = credentials match {
      case p @ Credentials.Provided(_) =>
        val authTokenQuery: DBIO[Seq[AuthToken]] = TableQuery[AuthTokenTable]
          .filter(c => c.tokenUserName === UUID.fromString(p.identifier) && c.isLoggedOut === false && c.validUntil > LocalDateTime.now() )
          .result
        val authToken: Option[AuthToken] = Await.result(
          db.run(authTokenQuery),
          Duration.apply(20, TimeUnit.SECONDS)
        ).headOption
        authToken match {
          case Some(a) =>
            if (p.verify(a.tokenSecret.toString)) {
              val credentialsQuery: DBIO[Seq[UserCredential]] = TableQuery[CredentialTable].filter(c => c.id === a.credentialId).result
              Await.result(
                db.run(credentialsQuery),
                Duration.apply(20, TimeUnit.SECONDS)
              ).headOption
            } else None;
          case _ => None
        }
      case _ => None
    }

    // Router
    lazy val route = handleRejections(corsRejectionHandler) {
      cors() {
        concat(
          pathEndOrSingleSlash (indexRoute),
          pathPrefix("credentials") (credentialsRoute),
          pathPrefix("auth") (authRoute),
          pathPrefix("camttransactions") (camtTransactionRoutes),
          pathPrefix("process") (processRoutes),
        )
      }
    }

    //individual routes
    lazy val credentialsRoute =
      authenticateBasic("secured site", BasicAuthAuthenticator) {
        authToken => pathEndOrSingleSlash {
          post {
            decodeRequest {
              entity(as[UserCredential]) { credential =>
                val credentialsQuery: DBIO[Seq[UserCredential]] = TableQuery[CredentialTable].filter(c => c.email === credential.email).result
                val credentials: Seq[UserCredential] = Await.result(
                  db.run(credentialsQuery),
                  Duration.apply(20, TimeUnit.SECONDS)
                )
                credentials match {
                  case Vector() =>
                    createCredential(credential)
                    complete(StatusCodes.Created, "credentials created")
                  case _ =>
                    complete(StatusCodes.BadRequest, "Email already exists.")
                }
              }
            }
          }
        }
    }

     lazy val authRoute = pathEndOrSingleSlash {
        post {
          decodeRequest {
            entity(as[UserCredential]) { credential =>
              val credentialsQuery: DBIO[Seq[UserCredential]] = TableQuery[CredentialTable].filter(c => c.email === credential.email).result
              val credentialsCandidate: Option[UserCredential] = Await.result(
                db.run(credentialsQuery),
                Duration.apply(20, TimeUnit.SECONDS)
              ).headOption
              credentialsCandidate match {
                case Some(c) => credential.password.isBcryptedSafeBounded(c.password) match {
                  case Success(true) =>
                    val authToken = createAuthToken(c)
                    complete(StatusCodes.OK, Base64.getEncoder.encodeToString(authToken.auth2BearerToken.getBytes))
                  case _ => complete(StatusCodes.Unauthorized, "No user matched the credentials.")
                }
                case _ => complete(StatusCodes.Unauthorized, "No user matched the credentials.")
              }
            }
          }
        }
    }

    lazy val indexRoute = get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>top level</h1>"))
    }

    lazy val processRoutes = authenticateBasic("secured site", BasicAuthAuthenticator) {
      authToken => path("camttransactions"){
        get {
          CamtService.apply()
          println("done")
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,"<h1>execution of camt service finished</h1>"))
        }
      }
    }

    lazy val camtTransactionRoutes =
      authenticateBasic("secured site", BasicAuthAuthenticator) {
        authToken => concat(
          pathEndOrSingleSlash {
            get {
              parameters("iban".?, "startvaluedate".?, "endvaluedate".?) {
                (iban, startValueDate, endValueDate) =>
                  println(s"iban = $iban")
                  val startValueLocalDateTime = LocalDateTime.parse(startValueDate.getOrElse("2019-01-01T00:00"))
                  val endValueLocalDateTime = endValueDate match {
                    case None => LocalDateTime.now()
                    case Some(string: String) => LocalDateTime.parse(string)
                  }
                  val camtTransactionsFuture = camtTransactionRepository.getCamtTransactions(iban, startValueLocalDateTime, endValueLocalDateTime)
                  val camtTransactionList: Seq[CamtTransaction] = Await.result(camtTransactionsFuture, Duration.apply(20, TimeUnit.SECONDS))
                  val camtTransactionJson = camtTransactionList.toJson //.map(c => SmallCamtTransaction(c.iban, c.bookingDate, c.valueDate, c.currency, c.amount, c.additionalInfo)).toJson
                  complete(HttpEntity(ContentTypes.`application/json`, camtTransactionJson.toString))
              }
            }
          },
          path("small") {
            get {
              parameters("iban".?, "startvaluedate".?, "endvaluedate".?) {
                (iban, startValueDate, endValueDate) =>
                  val startValueLocalDateTime = LocalDateTime.parse(startValueDate.getOrElse("2019-01-01T00:00"))
                  val endValueLocalDateTime = endValueDate match {
                    case None => LocalDateTime.now()
                    case Some(string: String) => LocalDateTime.parse(string)
                  }
                  val smallCamtTransactionsFuture = camtTransactionRepository.getSmallCamtTransactions(iban, startValueLocalDateTime, endValueLocalDateTime)
                  val smallCamtTransactionList: Seq[SmallCamtTransaction] = Await.result(smallCamtTransactionsFuture, Duration.apply(20, TimeUnit.SECONDS))
                  val smallCamtTransactionJson = smallCamtTransactionList.toJson //.map(c => SmallCamtTransaction(c.iban, c.bookingDate, c.valueDate, c.currency, c.amount, c.additionalInfo)).toJson
                  complete(HttpEntity(ContentTypes.`application/json`, smallCamtTransactionJson.toString))
              }
            }
          },
          path(Segment) { uuidString =>
            get {
              complete(
                HttpEntity(
                  ContentTypes.`application/json`,
                  Await.result(camtTransactionRepository.find(UUID.fromString(uuidString)), Duration.apply(20, TimeUnit.SECONDS)).toJson.toString
                )
              )
            }
          }
        )
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}