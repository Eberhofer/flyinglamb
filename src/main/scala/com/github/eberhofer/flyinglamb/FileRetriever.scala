package com.github.eberhofer.flyinglamb

import java.io.File

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import CamtFileParser.WriteCamtFile
import scala.xml.Elem


object FileRetriever {
  def apply(): Behavior[FileRetrieverRequest] = Behaviors.setup[FileRetrieverRequest](context => new FileRetriever(context))
  sealed trait FileRetrieverRequest
  final case object ProcessCAMTFiles extends FileRetrieverRequest
  final case object FileRetrieverStop extends FileRetrieverRequest

  sealed trait FileRetrieverResponse
  final case object ProcessedCAMTFiles extends FileRetrieverResponse

}

class FileRetriever(context: ActorContext[FileRetriever.FileRetrieverRequest]) extends AbstractBehavior[FileRetriever.FileRetrieverRequest](context) {
  import FileRetriever._

  context.log.info(" legt los")
  val filePath = context.system.settings.config.getString("filePath")
  val files = getListOfFiles(filePath)
  var counter = 0
  val sanji = context.spawn(DatabaseHandler("flyinglamb"),"sanji-flyinglamb") // the db handler

  override def onMessage(msg: FileRetrieverRequest): Behavior[FileRetrieverRequest] = msg match {
    case ProcessCAMTFiles =>
      context.log.info("FileRetriever gets to work")
      files
          .filter(f => f.getName.take(1) != '.')
        .foreach { f =>
          counter += 1
          println(f.getName)
          val camtFileParser = context.spawn(CamtFileParser(f.getPath, f.getName, sanji), "camt_file_parser_" + counter.toString)
          camtFileParser ! WriteCamtFile
        }
      this
    case FileRetrieverStop =>
      Behaviors.stopped
    case _ => Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[FileRetrieverRequest]] = {
    case PostStop =>
      context.log.info ("Luffy stopped")
      this
  }

  private def getListOfFiles(path: String): List[File] = {
    val d = new File(path)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }
}

