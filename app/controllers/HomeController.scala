package controllers

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.{ActorMaterializer, Attributes, IOResult, Materializer}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import javax.inject._
import play.api._
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Failure

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents)(implicit system: ActorSystem) extends AbstractController(cc){

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def tempFileUpload() = Action.async(parse.multipartFormData) { request =>
    println("tempFileUpload starting")
    val file = request.body.files.head
    val readFileBytes = FileIO.fromPath(file.ref.path).runFold(ByteString.empty)((cur,bs) => cur ++ bs)
    readFileBytes.map(_.decodeString("US-ASCII")).map(Ok(_))
  }

  def fileStreamUpload(): Action[MultipartFormData[Source[ByteString, _]]] = Action.async(parse.multipartFormData(handleFilePartAsStream)) { request =>
    // we never get here, due to an issue with accumulator.source.
    // See https://github.com/playframework/playframework/issues/7119 for details
    val file = request.body.files.head
    val fileData = file.ref.runFold(ByteString.empty)((state,bs) => state ++ bs)
    val decoded = fileData.map(_.decodeString("US-ASCII"))

    decoded.map(Ok(_))
  }

  def handleFilePartAsStream: FilePartHandler[Source[ByteString, _]] = {
      case FileInfo(partName, fileName, contentType, _) =>
        // This deadlocks, should not use until core issue is resolved.
        Accumulator.source[ByteString].map { source =>
          FilePart(partName, fileName, contentType, source)
        }
  }

  def fileSinkUpload = Action(parse.multipartFormData(handleFilePartAsSink)) { request =>
    val file = request.body.files.head.ref
    Ok(file.decodeString("US-ASCII"))
  }

  def handleFilePartAsSink: FilePartHandler[ByteString] = {
    case FileInfo(partName, fileName, contentType, _) =>
      Accumulator(myWsSink).map{ data =>
        FilePart(partName,fileName,contentType,data)
      }
  }

  def myWsSink: Sink[ByteString,Future[ByteString]] = {
    Sink.fold(ByteString.empty)((state,bs) => state ++ bs)
  }
}
