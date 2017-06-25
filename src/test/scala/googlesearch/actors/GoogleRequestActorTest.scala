package googlesearch.actors

import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import googlesearch.actors.GoogleRequestActor._
import googlesearch.actors.SearchHistoryActor.{AcknowledgeRemembering, RememberString}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FunSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class GoogleRequestActorTest
  extends TestKit(ActorSystem("GoogleRequestActorTest")) with ImplicitSender
    with FunSpecLike with BeforeAndAfterAll with MockFactory {

  val historyActorMock: ActorRef = system.actorOf(Props(new Actor {
    override def receive: Receive = {
      case RememberString(id, _) => sender() ! AcknowledgeRemembering(id)
    }
  }))

  describe("Google search request actor") {
    describe("Enough quota") {
      it("should ask history to save query") {
        val historyActor = TestProbe()

        val actor = system.actorOf(
          props(historyActor.ref, mockFunction[HttpRequest, Future[HttpResponse]])
        )

        actor ! SearchFor("42", "google")

        historyActor.expectMsg(SearchHistoryActor.RememberString("42", "google"))
      }

      it("should request Google") {
        val httpClient = mockFunction[HttpRequest, Future[HttpResponse]]

        httpClient.expects(where {(r: HttpRequest) => r.uri.toString().contains("google42") })
          .returning(Future.successful(HttpResponse(status = StatusCodes.OK)))

        val actor = system.actorOf(props(historyActorMock, httpClient))

        actor ! SearchFor("42", "google42")

        expectMsg(SearchResults("42", Seq.empty))
      }

      it("should return URLs for the \"akka\" keyword") {
        val httpClient = mockFunction[HttpRequest, Future[HttpResponse]]

        httpClient.expects(*)
          .returning(Future.successful(HttpResponse(
            status = StatusCodes.OK,
            // TODO: should stream bytes from file instead of loading file into String
            entity = HttpEntity.Strict(ContentTypes.NoContentType, data = ByteString.fromString(
              new String(
                Files.readAllBytes(
                  Paths.get(getClass.getResource("/search-for-akka-response.html").toURI)
                )
              )
            ))
          )))

        val actor = system.actorOf(props(historyActorMock, httpClient))

        actor ! SearchFor("42", "akka")

        expectMsgPF() {
          case SearchResults("42", urls) if urls.contains("https://github.com/akka/akka") =>
        }
      }
    }

    describe("Not enough quota") {
      it("should return error when no quota left") {
        val httpClient = mockFunction[HttpRequest, Future[HttpResponse]]

        val actor = system.actorOf(Props(new GoogleRequestActor(
          historyActorMock,
          httpClient,
          GoogleQueryQuota(0, 42.minutes)
        )))

        actor ! SearchFor("42", "foo")

        expectMsg(QueryLimitExceeded("42"))
      }

      it("should return error only for out of quota requests") {
        val httpClient = mockFunction[HttpRequest, Future[HttpResponse]]

        httpClient
          .expects(*)
          .atLeastOnce()
          .returning(Future.successful(HttpResponse(status = StatusCodes.OK)))

        val actor = system.actorOf(Props(new GoogleRequestActor(
          historyActorMock,
          httpClient,
          GoogleQueryQuota(2, 42.minutes)
        )))

        actor ! SearchFor("0", "foo")
        actor ! SearchFor("1", "foo")
        actor ! SearchFor("2", "foo")

        expectMsg(QueryLimitExceeded("2"))
        expectMsg(SearchResults("0", Seq.empty))
        expectMsg(SearchResults("1", Seq.empty))
      }

      it("should replenish quota after some period defined") {
        val httpClient = mockFunction[HttpRequest, Future[HttpResponse]]

        httpClient
          .expects(*)
          .atLeastOnce()
          .returning(Future.successful(HttpResponse(status = StatusCodes.OK)))

        val actor = system.actorOf(Props(new GoogleRequestActor(
          historyActorMock,
          httpClient,
          GoogleQueryQuota(1, 100.milliseconds)
        )))

        actor ! SearchFor("0", "foo")
        actor ! SearchFor("1", "foo")

        expectMsg(QueryLimitExceeded("1"))
        expectMsg(SearchResults("0", Seq.empty))

        Thread.sleep(200)

        actor ! SearchFor("2", "foo")
        expectMsg(SearchResults("2", Seq.empty))
      }
    }
  }
}
