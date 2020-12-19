package com.softwaremill.bootzooka.speechtotext

import com.google.cloud.speech.v1p1beta1.{RecognitionConfig, StreamingRecognitionConfig, StreamingRecognizeRequest}

object SpeechRecognitionConfig {
  private val recognitionConfig = RecognitionConfig.newBuilder
    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
    .setLanguageCode("en_US")
    .setSampleRateHertz(16000)
    .setModel("command_and_search")
    .build

  def apply(): StreamingRecognitionConfig = StreamingRecognitionConfig.newBuilder
    .setConfig(recognitionConfig)
    .setInterimResults(true)
    .build

  val configRequest: StreamingRecognizeRequest = StreamingRecognizeRequest.newBuilder.setStreamingConfig(SpeechRecognitionConfig()).build
}
