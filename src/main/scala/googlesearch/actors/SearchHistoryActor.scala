package googlesearch.actors

import akka.actor.{Actor, Props}
import googlesearch.actors.SearchHistoryActor._

import scala.collection.GenSeq

/**
  * Stores strings to remember and gives away stored strings.
  * <br><br>
  * Sending `RememberString(toRemember)` causes remembering and replying with
  * `Acknowledge`.
  * Sending `Remind` causes replying with `Remembered(strings)`.
  * <br><br>
  * Doesn't persist memorized strings but stores it in the memory.
  */
class SearchHistoryActor extends Actor {
  def receive = handlingHistory(Vector.empty)

  def handlingHistory(remembered: Vector[String]): Receive = {
    case RememberString(requestId, toRemember) =>
      sender ! AcknowledgeRemembering(requestId)
      context.become(handlingHistory(remembered :+ toRemember))

    case Remind(requestId) => sender ! Remembered(requestId, remembered)
  }
}

object SearchHistoryActor {
  def props: Props = Props[SearchHistoryActor]

  case class RememberString(requestId: String, toRemember: String)
  case class AcknowledgeRemembering(requestId: String)

  case class Remind(requestId: String)
  case class Remembered(requestId: String, strings: GenSeq[String])
}
