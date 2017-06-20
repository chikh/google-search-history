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
      it("should restore one remembered string") {}

      it("should restore all remembered strings") {}

      it("should respond with empty list if no strings stored") {}
    }
  }

  override protected def afterAll() {
    shutdown()
  }
}
