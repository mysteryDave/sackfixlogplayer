package org.sackfix.client

import java.time.{LocalDateTime, LocalTime}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.sackfix.boostrap._
import org.sackfix.codec.SfDecodeBytesToTuples
import org.sackfix.common.message.{SfFixUtcTime, SfMessage}
import org.sackfix.field._
import org.sackfix.fix44._
import org.sackfix.session.SfSessionId

import scala.collection.mutable
import scala.io.Source

/**
  * This is a session agnostic actor (shouldn't care if it is a server or a client) and can be injected into either.
  *
  * Its purpose is to stream fix messages from a file log on to a fix session.
  * This differs from the file store used as playback as the files read need to be human readable (to create test sets.)
  * Ideally this will read from the file and send business messages according to the timestamps in the file.
  *
  * Backpressure is not implemented in SackFix for IO Buffer filling up on read or write.  If you want to
  * add it please feel free.  Note that you should probably NOT send out orders if you have ACKs outstanding.
  * This will pretty much avoid all back pressure issues. ie if sendMessages.size>1 wait
  *
  * In this case we can ignore above and leave it the responsibility of the log file supplier to ensure the messages are timed reasonably.
  */
object ClientOMSMessageActor {
  def props(): Props = Props(new ClientOMSMessageActor)
}

class ClientOMSMessageActor extends Actor with ActorLogging {
  private val REPLAY_LOG_FILENAME: String = "E:/replay_me.decoded"
  private val REPLAY_PRECISION_NANOS: Long = 1000000000
  private val decoder: SfDecodeBytesToTuples = new SfDecodeBytesToTuples(false)
  private val fileIterator: Iterator[String] = Source.fromFile(REPLAY_LOG_FILENAME).getLines()  //eagerly instantiated. Maybe better lazily in case session never opens?

  //Stateful
  private val sentMessages = mutable.HashMap.empty[String, Long]
  private var orderId = 0
  private var isSessionOpen = false
  private var queuedLogLine: Option[String] = Option.empty

  override def receive: Receive = {
    case FixSessionOpen(sessionId: SfSessionId, sfSessionActor: ActorRef) =>
      log.info(s"Session ${sessionId.id} is OPEN for business")
      isSessionOpen = true
      startPlayingFromFile() //start replay
    case FixSessionClosed(sessionId: SfSessionId) =>
      // Anything not acked did not make it our to the TCP layer - even if acked, there is a risk
      // it was stuck in part or full in the send buffer.  So you should worry when sending fix
      // using any tech that the message never arrives.
      log.info(s"Session ${sessionId.id} is CLOSED for business")
      isSessionOpen = false
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
  }

  def logTimeInPast(logTime: String): Boolean = LocalTime.parse(logTime).compareTo(LocalTime.now().plusNanos(REPLAY_PRECISION_NANOS)) <= 0

  def startPlayingFromFile(): Unit = {
    //Can assume nothing enqueued
    queuedLogLine = Option(fileIterator.next())
    if (queuedLogLine.isDefined) {
      var lineElements: Array[String] = queuedLogLine.get.split(" ")
      while (fileIterator.hasNext && logTimeInPast(lineElements(0))) {
        log.info("Play message from file:{}", queuedLogLine.get)
        queuedLogLine = Option(fileIterator.next())
        lineElements = queuedLogLine.get.split(" ")
      }
      //Two exit conditions !file.hasNext or queuedLine is for future.
      if (fileIterator.hasNext) log.info("NO MORE MESSAGES TO PLAY. Queueing:{}", queuedLogLine.get)
      else log.info("EOF - All messages from file have been (re)played.")
    }
  }

  def playMessagesFromQueue(): Unit = {
    //can assume something enqueued
    log.info("Play message from queue:{}", queuedLogLine.get)
    startPlayingFromFile()
  }

  def sendANos(fixSessionActor: ActorRef): Unit = {
    if (isSessionOpen) {
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
