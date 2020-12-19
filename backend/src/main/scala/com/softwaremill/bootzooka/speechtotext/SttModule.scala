package com.softwaremill.bootzooka.speechtotext

trait SttModule {
  lazy val sttpApi = new SpeechToText
}
