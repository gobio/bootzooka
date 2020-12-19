package com.softwaremill.bootzooka.speechtotext

import cats.implicits._
import com.google.api.gax.rpc.{ClientStream, ResponseObserver, StreamController}
import com.google.cloud.speech.v1p1beta1.{SpeechClient, StreamingRecognizeRequest, StreamingRecognizeResponse}
import com.google.protobuf.ByteString
import fs2.Pipe
import fs2.Stream.{bracket, eval}
import fs2.concurrent.Queue
import fs2.concurrent.Queue.unbounded
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.HttpRoutes
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Codec._
import sttp.tapir._
import sttp.tapir.server.http4s._
import sttp.ws.WebSocketFrame

class SpeechToText {
  val speechClient: SpeechClient = SpeechClient.create

  def handleWebSocket: Pipe[Task, WebSocketFrame, WebSocketFrame] = audioStream =>
    for {
      queue <- eval(unbounded[Task, String])
      sttStream <- bracket(connectStt(queue))(stt => Task(stt.closeSend()))
      audioChunk <- audioStream.collect {
        case binary: WebSocketFrame.Binary => binary.payload
      }
      sttResultStream = queue.dequeue
      transcript <- eval(sendAudio(sttStream, audioChunk)).drain.mergeHaltBoth(sttResultStream)
    } yield WebSocketFrame.text(transcript)


  private def sendAudio(sttStream: ClientStream[StreamingRecognizeRequest], data: Array[Byte]) =
    Task(StreamingRecognizeRequest.newBuilder.setAudioContent(ByteString.copyFrom(data)).build)
      .flatMap(req => Task(sttStream.send(req)))


  private def connectStt(queue: Queue[Task, String]): Task[ClientStream[StreamingRecognizeRequest]] =
    Task(speechClient.streamingRecognizeCallable.splitCall(new RecognitionObserver(queue)))
      .flatTap(stt => Task(stt.send(SpeechRecognitionConfig.configRequest)))


  private val wsEndpoint =
    endpoint.get
      .in("stt")
      .out(webSocketBody[WebSocketFrame, CodecFormat.TextPlain, WebSocketFrame, CodecFormat.TextPlain](Fs2Streams[Task]))

  val wsRoutes: HttpRoutes[Task] = wsEndpoint.toRoutes(_ => Task.pure(Right(handleWebSocket)))
}

class RecognitionObserver(queue: Queue[Task, String]) extends ResponseObserver[StreamingRecognizeResponse] {
  override def onStart(controller: StreamController): Unit = {
  }

  override def onResponse(response: StreamingRecognizeResponse): Unit = {
    val result = response.getResultsList.get(0)
    val isFinal = result.getIsFinal
    val transcript = result.getAlternativesList.get(0).getTranscript
    val msg = s"""{"final": $isFinal, "text" : "$transcript"}"""
    queue.enqueue1(msg).runSyncUnsafe()
  }

  override def onError(t: Throwable): Unit = {
  }

  override def onComplete(): Unit = {
  }
}
