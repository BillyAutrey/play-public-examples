package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.libs.Files
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import play.api.test._
import play.api.test.Helpers._
import java.nio.file.{Files => JFiles}

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import org.scalatest.AsyncWordSpec
import play.api.http.HeaderNames

import scala.concurrent.ExecutionContext

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "HomeController GET" should {

    "render the index page from a new instance of controller" in {
      implicit val system: ActorSystem = app.actorSystem
      val controller = new HomeController(stubControllerComponents())
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }

    "render the index page from the router" in {
      val request = FakeRequest(GET, "/")
      val home = route(app, request).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }

    "process a multipartform as a temp file" in {
      val controller = inject[HomeController]
      implicit val mat: Materializer = app.materializer
      implicit val ec: ExecutionContext = app.materializer.executionContext

      val file = SingletonTemporaryFileCreator.create("tmp","txt")
      val writeResult = Source.single(ByteString("testdata")).runWith(FileIO.toPath(file.path))

      val fileSeq = Seq(FilePart("key", "tmp.txt", None, file, JFiles.size(file.path)))
      val fileRequest = FakeRequest().withBody {
        MultipartFormData(Map.empty, fileSeq, Nil)
      }

      val fileStreamResult = writeResult.flatMap( _ => controller.tempFileUpload().apply(fileRequest))
      contentAsString(fileStreamResult) mustBe "testdata"
      status(fileStreamResult) mustBe OK
    }

    "stream a multipartform into a sink" in {
      val controller = inject[HomeController]
      implicit val mat: Materializer = app.materializer
      implicit val ec: ExecutionContext = app.materializer.executionContext

      lazy val body =
        """
          |--boundary
          |Content-Disposition: form-data; name="file"; filename="foo"
          |Content-Type: text/plain
          |
          |testdata
          |--boundary--
          |""".stripMargin.linesIterator.mkString("\r\n")

      val file = Source.single(ByteString(body))

      val fileSeq = Seq(FilePart("key", "tmp.txt", None, file, 8))
      val fileRequest = FakeRequest().withBody {
        MultipartFormData(Map.empty, fileSeq, Nil)
      }.withHeaders(HeaderNames.CONTENT_TYPE -> "multipart/form-data; boundary=boundary")

      val fileStreamResult = controller.fileSinkUpload().apply(fileRequest).run(file)
      contentAsString(fileStreamResult) mustBe "testdata"
      status(fileStreamResult) mustBe OK
    }

    // This fails, because it expects a header.
//    "stream a multipartform as a temp file" in {
//      val controller = inject[HomeController]
//      implicit val mat: Materializer = app.materializer
//      implicit val ec: ExecutionContext = app.materializer.executionContext
//
//      val file = Source.single(ByteString("testdata"))
//
//      val fileSeq = Seq(FilePart("key", "tmp.txt", None, file, 8))
//      val fileRequest = FakeRequest().withBody {
//        MultipartFormData(Map.empty, fileSeq, Nil)
//      }
//
//      val fileStreamResult = controller.fileStreamUpload().apply(fileRequest)
//      contentAsString(fileStreamResult) mustBe "testdata"
//      status(fileStreamResult) mustBe OK
//    }

    //This also fails, because "Unexpected end of input"
    // Problem 1 - Multipart.partParser is being run here, but not for temp files.
    // Problem 2 - MultipartFormData with TempFile is the only way to use helpers for this.
    // Unclear what about TempFile handles the boundaries automatically in tests.
    // Also unclear about whether this carries over into actual multipartform uploads
//    "stream a multipartform as a temp file" in {
//      val controller = inject[HomeController]
//      implicit val mat: Materializer = app.materializer
//      implicit val ec: ExecutionContext = app.materializer.executionContext
//
////      lazy val body =
////        """
////          |--boundary
////          |Content-Disposition: form-data; name="file"; filename="foo"
////          |Content-Type: text/plain
////          |
////          |testdata
////          |
////          |--boundary--
////          |""".stripMargin.linesIterator.mkString("\r\n")
////
////      val file = Source.single(ByteString(body))
//      val bytestringdata = ByteString("testdata")
//      val file = Source.single(bytestringdata)
//
//      val fileSeq = Seq(FilePart("file", "tmp.txt", Some("text/plain"), file))
//      val fileRequest = FakeRequest().withBody {
//        MultipartFormData(Map.empty, fileSeq, List.empty)
//      }.withHeaders(HeaderNames.CONTENT_TYPE -> "multipart/form-data; boundary=boundary")
//
//      val fileStreamResult = controller.fileStreamUpload().apply(fileRequest)//.run(ByteString(body))
//      contentAsString(fileStreamResult) mustBe "testdata"
//      status(fileStreamResult) mustBe OK
//    }
  }
}
