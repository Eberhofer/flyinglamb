package com.github.eberhofer.flyinglamb

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.eberhofer.flyinglamb.FileRetriever.ProcessCAMTFiles


/**
 * Top level user actor, this will handle the failures of the child actors.
 */
object Launcher {
  sealed trait Command
  case object ShutDownGracefully

  def apply(): Behavior[FileRetriever] = Behaviors
    .setup {
      context =>
        context.log.info("CAMT file processor started")
        val a = context.spawn(FileRetriever(), "file-retriever")
        a ! FileRetriever.ProcessCAMTFiles
        Behaviors.same
    }
}

