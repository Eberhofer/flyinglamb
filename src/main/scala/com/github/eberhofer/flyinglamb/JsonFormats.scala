package com.github.eberhofer.flyinglamb

//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  //implicit val localDateTimeFormat = jsonFormat1

  //implicit val userJsonFormat = jsonFormat3(CamtFile)
  //implicit val usersJsonFormat = jsonFormat1(CamtTransaction)

  //implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
}
