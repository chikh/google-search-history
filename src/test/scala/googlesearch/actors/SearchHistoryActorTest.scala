package googlesearch.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import googlesearch.actors.SearchHistoryActor._
import org.scalatest.{BeforeAndAfterAll, FunSpecLike}

class SearchHistoryActorTest
  extends TestKit(ActorSystem("SearchHistoryActorTest")) with ImplicitSender
    with FunSpecLike with BeforeAndAfterAll {

  describe("SearchHistoryActor") {
    describe("String storing") {
      it("should acknowledge string storing") {
        val actor = system.actorOf(props)

        actor ! RememberString("0", "foo")
        expectMsg(AcknowledgeRemembering("0"))
      }
    }

    describe("History requesting") {
      it("should restore one remembered string") {
        val actor = system.actorOf(props)

        actor ! RememberString("0", "foo")
        expectMsg(AcknowledgeRemembering("0"))

        actor ! Remind("1")
        expectMsg(Remembered("1", Seq("foo")))
      }

      it("should preserve order of memoization") {
        val actor = system.actorOf(props)

        actor ! RememberString("0", "foo1")
        actor ! RememberString("1", "foo2")
        expectMsg(AcknowledgeRemembering("0"))
        expectMsg(AcknowledgeRemembering("1"))

        actor ! Remind("3")
        expectMsg(Remembered("3", Seq("foo1", "foo2")))
      }

      it("should respond with empty list if no strings stored") {
        val actor = system.actorOf(props)

        actor ! Remind("0")
        expectMsg(Remembered("0", Seq.empty))
      }
    }
  }

  override protected def afterAll() {
    shutdown()
  }
}
