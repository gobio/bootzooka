import React, {useEffect, useState} from "react";
import Container from "react-bootstrap/Container";
import {Button} from "react-bootstrap";

const sampleRate = 16000

const loadPCMWorker = (audioContext: AudioContext) =>
  audioContext.audioWorklet.addModule('/pcmWorker.js')

const getMediaStream = () =>
  navigator.mediaDevices.getUserMedia({
    audio: {
      deviceId: "default",
      sampleRate: sampleRate,
      sampleSize: 16,
      channelCount: 1
    },
    video: false
  })

const captureAudio = (audioContext: AudioContext, stream: MediaStream, output: (data: any) => void) => {
  const source: MediaStreamAudioSourceNode = audioContext.createMediaStreamSource(stream)
  const pcmWorker = new AudioWorkletNode(audioContext, 'pcm-worker', {outputChannelCount: [1]})
  source.connect(pcmWorker)
  pcmWorker.port.onmessage = event => output(event.data)
  pcmWorker.port.start()
}

interface WordRecognized {
  final: boolean,
  text: string
}

const SpeechToText: React.FC = () => {

  const [connection, setConnection] = useState<WebSocket>()
  const [currentRecognition, setCurrentRecognition] = useState<string>()
  const [recognitionHistory, setRecognitionHistory] = useState<string[]>([])

  const speechRecognized = (data: WordRecognized) => {
    if (data.final) {
      setCurrentRecognition("...")
      setRecognitionHistory(old => [data.text, ...old])
    } else setCurrentRecognition(data.text + "...")
  }


  const connect = () => {
    connection?.close()
    const conn = new WebSocket("ws://localhost:8080/ws/stt")
    conn.onmessage = event => speechRecognized(JSON.parse(event.data))
    setConnection(conn)
  }

  const disconnect = () => {
    connection?.close()
    setConnection(undefined)
  }

  useEffect(() => {
    if (connection) {
      const audioContext = new window.AudioContext({sampleRate})
      const stream = Promise.all([loadPCMWorker(audioContext), getMediaStream()])
        .then(([_, stream]) => {
          captureAudio(audioContext, stream, data => connection.send(data))
          return stream
        })
      return () => {
        stream.then(stream => stream.getTracks().forEach(track => track.stop()))
        audioContext.close()
      }
    }
  }, [connection])

  return (
    <>
      <Container fluid className="py-5 bg-primary text-light text-center">
        <Container>
          <Button className="btn-outline-light" onClick={connect}>Start</Button>{" "}
          <Button className="btn-outline-light" onClick={disconnect}>Stop</Button>
        </Container>
      </Container>
      <Container className="py-5 text-center">
        <h2>{currentRecognition}</h2>
        {recognitionHistory.map((tx, idx) => <h2 key={idx}>{tx}</h2>)}
      </Container>
    </>
  )
}

export default SpeechToText;
