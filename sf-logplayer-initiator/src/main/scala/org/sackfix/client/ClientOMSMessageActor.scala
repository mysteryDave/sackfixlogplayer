package org.sackfix.client

import java.time.{Instant, ZoneId}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.sackfix.boostrap._
import org.sackfix.codec.{DecodingFailedData, SfDecodeTuplesToMsg}
import org.sackfix.common.message.SfMessage
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
  private val REPLAY_LOG_FILENAME: String = "C:/Users/DT/Documents/MSc/PROJECT/ScalaFIX/example.fix.txt"
  private val REPLAY_PRECISION_MILLIS: Long = 2000 //try to send within this range from recorded send time
  private val MAX_SEND_QUEUE_SIZE: Int = 1
  private val IGNORE_MESSAGE_TYPES: Set[String] = Set("0", "1", "2", "3", "4", "A")

  private val fileIterator: Iterator[String] = Source.fromFile(REPLAY_LOG_FILENAME).getLines() //eagerly instantiated. Maybe better lazily in case session never opens?

  //Stateful
  private val sentMessages = mutable.HashMap.empty[String, Long]
  private var orderId = 0
  private var isSessionOpen = false
  private var queuedMessage: Option[SfMessage] = Option.empty


  override def receive: Receive = {
    case FixSessionOpen(sessionId: SfSessionId, sfSessionActor: ActorRef) =>
      log.info(s"Session ${sessionId.id} is OPEN for business")
      isSessionOpen = true
      //start replay
      readMessageFromFile()
      playMessageFromFile(sfSessionActor)
    case FixSessionClosed(sessionId: SfSessionId) =>
      // Anything not acked did not make it our to the TCP layer - even if acked, there is a risk
      // it was stuck in part or full in the send buffer.  So you should worry when sending fix
      // using any tech that the message never arrives.
      log.info(s"Session ${sessionId.id} is CLOSED for business")
      if (queuedMessage.isDefined) log.info("Unplayed logs beginning {}", queuedMessage.get)
      isSessionOpen = false
    case BusinessFixMessage(sessionId: SfSessionId, sfSessionActor: ActorRef, message: SfMessage) =>
      log.info(s"Ignoring received message: ${message.toString}")
    case BusinessFixMsgOutAck(sessionId: SfSessionId, sfSessionActor: ActorRef, correlationId: String) =>
      // You should have a HashMap of stuff you send, and when you get this remove from your set.
      // Read the Akka IO TCP guide for ACK'ed messages and you will see
      sentMessages.remove(correlationId).foreach(tstamp =>
        log.debug(s"$correlationId send duration = ${(System.nanoTime() - tstamp) / 1000} Micros"))
      playMessageFromFile(sfSessionActor)
    case BusinessRejectMessage(sessionId: SfSessionId, sfSessionActor: ActorRef, message: SfMessage) =>
      log.warning(s"Session ${sessionId.id} has rejected the message ${message.toString()}")
  }

  def logTimeInPast(message: SfMessage): Boolean = message.header.sendingTimeField.value.atZone(ZoneId.of("UTC")).toInstant.isBefore(Instant.now.plusMillis(REPLAY_PRECISION_MILLIS))

  def readMessageFromFile(): Unit = do {
      val logLine: String = fileIterator.next
      log.info("READ fix?{} '{}'", logLine.contains("8=FIX"), logLine)
      queuedMessage = if (logLine.contains("8=FIX")) SfDecodeTuplesToMsg.decodeFromStr(logLine.substring(logLine.indexOf("8=FIX")), readLogFailed, Option.empty)
      else Option.empty
      if (queuedMessage.isDefined && IGNORE_MESSAGE_TYPES.contains(queuedMessage.get.body.msgType)) queuedMessage = Option.empty
      log.info("READ MESSAGE empty?{}, next?{} '{}'", queuedMessage.isEmpty, fileIterator.hasNext, queuedMessage)
    } while (queuedMessage.isEmpty && fileIterator.hasNext)

  def readLogFailed: DecodingFailedData => Unit = { failData: DecodingFailedData => log.warning("Failed to decode a fix message from the log file: {}", failData) }

  def playMessageFromFile(fixSessionActor: ActorRef): Unit = if (queuedMessage.isDefined && logTimeInPast(queuedMessage.get) && sentMessages.size < MAX_SEND_QUEUE_SIZE) {
      log.info("Sending message from log: '{}'", queuedMessage.get)
      sentMessages(queuedMessage.get.body.fixStr) = System.nanoTime()
      fixSessionActor ! BusinessFixMsgOut(queuedMessage.get.body, queuedMessage.get.body.fixStr)
      queuedMessage = Option.empty
      readMessageFromFile()
    } else if (queuedMessage.isDefined && !logTimeInPast(queuedMessage.get)) log.info("Play this one later: '{}'", queuedMessage.get)
}