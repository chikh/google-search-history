package googlesearch.actors

import akka.actor.{Actor, Props}

case class RememberString(toRemember: String)
case object AcknowledgeRemembering

case object Remind
case class Remembered(strings: Seq[String])

/**
  * Stores strings to remember and gives away stored strings.
  *
  * Sending RememberString(toRemember) causes remembering and replying with
  * Acknowledge.
  * Sending Remind causes replying with Remembered(strings).
  */
class SearchHistoryActor extends Actor {
  def receive: Receive = ???
}

object SearchHistoryActor {
  def props: Props = Props[SearchHistoryActor]
}
