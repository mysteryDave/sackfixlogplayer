package org.sackfix.client

import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.sackfix.boostrap._
import org.sackfix.common.message.{SfFixUtcTime, SfMessage}
import org.sackfix.field._
import org.sackfix.fix44._
import org.sackfix.session.SfSessionId

import scala.collection.mutable

/**
  * You must implement an actor for business messages.
  * You should inject it into the SfInitiatorActor or SfAcceptorActor depending on
  * if you are a server or a client
  *
  * Backpressure is not implemented in SackFix for IO Buffer filling up on read or write.  If you want to
  * add it please feel free.  Note that you should probably NOT send out orders if you have ACKs outstanding.
  * This will pretty much avoid all back pressure issues. ie if sendMessages.size>1 wait
  */
object ClientOMSMessageActor {
  def props(): Props = Props(new ClientOMSMessageActor)
}

class ClientOMSMessageActor extends Actor with ActorLogging {
  private val REPLAY_LOG_FILENAME = "E:/replay_me.decoded"
  private val sentMessages = mutable.HashMap.empty[String, Long]
  private var orderId = 0
  private var isOpen = false

  override def receive: Receive = {
    case FixSessionOpen(sessionId: SfSessionId, sfSessionActor: ActorRef) =>
      log.info(s"Session ${sessionId.id} is OPEN for business")
      isOpen = true
      playMessagesFromFile() //start replay
    case FixSessionClosed(sessionId: SfSessionId) =>
      // Anything not acked did not make it our to the TCP layer - even if acked, there is a risk
      // it was stuck in part or full in the send buffer.  So you should worry when sending fix
      // using any tech that the message never arrives.
      log.info(s"Session ${sessionId.id} is CLOSED for business")
      isOpen = false
    case BusinessFixMessage(sessionId: SfSessionId, sfSessionActor: ActorRef, message: SfMessage) =>
      onBusinessMessage(sfSessionActor, message)
    case BusinessFixMsgOutAck(sessionId: SfSessionId, sfSessionActor: ActorRef, correlationId: String) =>
      // You should have a HashMap of stuff you send, and when you get this remove from your set.
      // Read the Akka IO TCP guide for ACK'ed messages and you will see
      sentMessages.get(correlationId).foreach(tstamp =>
        log.debug(s"$correlationId send duration = ${(System.nanoTime() - tstamp) / 1000} Micros"))
    case BusinessRejectMessage(sessionId: SfSessionId, sfSessionActor: ActorRef, message: SfMessage) =>
      log.warning(s"Session ${sessionId.id} has rejected the message ${message.toString()}")
  }

  /**
    * @param fixSessionActor This will be a SfSessionActor, but sadly Actor ref's are not typed as yet
    */
  def onBusinessMessage(fixSessionActor: ActorRef, message: SfMessage): Unit = {
    //We are a message pusher and don't care about or respond do incoming business messages.
    log.info(s"Ignoring received message: ${message.toString}" )
    playMessagesFromFile()
  }

  def playMessagesFromFile(): Unit = {
    val path: Path = Paths.get(REPLAY_LOG_FILENAME)
    Files.lines(path).forEach(line => log.info("Play message {}", line))
  }

  def sendANos(fixSessionActor: ActorRef): Unit = {
    if (isOpen) {
      // validation etc..but send back the ack
      // NOTE, AKKA is Asynchronous.  You have ZERO idea if this send worked, or coincided with socket close down and so on.
      val correlationId = "NOS" + LocalDateTime.now.toString
      sentMessages(correlationId) = System.nanoTime()
      orderId += 1
      fixSessionActor ! BusinessFixMsgOut(NewOrderSingleMessage(clOrdIDField = ClOrdIDField(orderId.toString),
        instrumentComponent = InstrumentComponent(symbolField = SymbolField("JPG.GB")),
        sideField = SideField({
          if (orderId % 2 == 0) SideField.Buy else SideField.Sell
        }),
        transactTimeField = TransactTimeField(SfFixUtcTime.now),
        orderQtyDataComponent = OrderQtyDataComponent(orderQtyField = Some(OrderQtyField(100))),
        ordTypeField = OrdTypeField(OrdTypeField.Market)), correlationId)
    }
  }
}