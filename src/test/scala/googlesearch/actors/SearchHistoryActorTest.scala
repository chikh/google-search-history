package googlesearch.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike}

class SearchHistoryActorTest
  extends TestKit(ActorSystem("SearchHistoryActorTest")) with ImplicitSender
    with FunSpecLike with BeforeAndAfterAll {

  describe("SearchHistoryActor") {
    describe("String storing") {
      it("should acknowledge string storing") {
        val actor = system.actorOf(SearchHistoryActor.props)

        actor ! RememberString("foo")
        expectMsg(AcknowledgeRemembering)
      }
    }

    describe("History requesting") {
      it("should restore one remembered string") {
        val actor = system.actorOf(SearchHistoryActor.props)

        actor ! RememberString("foo")
        expectMsg(AcknowledgeRemembering)

        actor ! Remind
        expectMsg(Remembered(Seq("foo")))
      }

      it("should preserve order of memoization") {
        val actor = system.actorOf(SearchHistoryActor.props)

        actor ! RememberString("foo1")
        expectMsg(AcknowledgeRemembering)
        actor ! RememberString("foo2")
        expectMsg(AcknowledgeRemembering)

        actor ! Remind
        expectMsg(Remembered(Seq("foo1", "foo2")))
      }

      it("should respond with empty list if no strings stored") {
        val actor = system.actorOf(SearchHistoryActor.props)

        actor ! Remind
        expectMsg(Remembered(Seq.empty))
      }
    }
  }

  override protected def afterAll() {
    shutdown()
  }
}
