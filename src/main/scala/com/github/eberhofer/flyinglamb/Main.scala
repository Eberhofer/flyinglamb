package com.github.eberhofer.flyinglamb

import akka.actor.typed.ActorSystem

object Main extends App {
  val system = ActorSystem[FileRetriever](Launcher(), "typed-camt-processor")
  Thread.sleep(60000)
  system.terminate()
}
