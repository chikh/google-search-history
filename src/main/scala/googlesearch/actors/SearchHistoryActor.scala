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
  var rememberedStrings: Vector[String] = Vector.empty

  def receive: Receive = {
    case RememberString(toRemember) =>
      rememberedStrings = rememberedStrings :+ toRemember
      sender ! AcknowledgeRemembering

    case Remind => sender ! Remembered(rememberedStrings)
  }
}

object SearchHistoryActor {
  def props: Props = Props[SearchHistoryActor]

  case class RememberString(toRemember: String)
  case object AcknowledgeRemembering

  case object Remind
  case class Remembered(strings: GenSeq[String])
}
