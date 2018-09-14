package org.sackfix.server

import akka.actor.ActorRef

trait SfFixLogPlayerInfo{}

case class ResumeLogPlay(sfSessionActor: ActorRef) extends SfFixLogPlayerInfo {}

trait LogPlayerCommsHandler {
  def handleFix(msg: SfFixLogPlayerInfo)
}
