package org.sackfix.client

import akka.actor.ActorRef


trait SfFixLogPlayerInfo {
}

case class ResumeLogPlay(sfSessionActor: ActorRef) {
}

trait LogPlayerCommsHandler {
  def handleFix(msg: SfFixLogPlayerInfo)
}