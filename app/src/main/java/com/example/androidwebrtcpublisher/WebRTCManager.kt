package com.example.androidwebrtcpublisher

import android.content.Context
import org.webrtc.*

class WebRTCManager(
    private val context: Context,
    private val localView: SurfaceViewRenderer
) {

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var eglBase: EglBase
    private lateinit var videoSource: VideoSource
    private lateinit var videoTrack: VideoTrack
    private lateinit var peerConnection: PeerConnection

    fun initialize() {

        val initOptions =
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()

        PeerConnectionFactory.initialize(initOptions)

        eglBase = EglBase.create()

        val options = PeerConnectionFactory.Options()

        peerConnectionFactory =
            PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()

        localView.init(eglBase.eglBaseContext, null)
        localView.setMirror(true)

        startCamera()
        createPeerConnection()
        createOffer()
    }

    private fun startCamera() {
        val videoCapturer = createCameraCapturer()

        videoSource = peerConnectionFactory.createVideoSource(false)

        val surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)

        videoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            videoSource.capturerObserver
        )

        videoCapturer?.startCapture(1280, 720, 30)

        videoTrack =
            peerConnectionFactory.createVideoTrack("VIDEO_TRACK", videoSource)

        videoTrack.addSink(localView)
    }

    private fun createPeerConnection() {

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    println("ICE Candidate: $candidate")
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    println("Connection State: $newState")
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}
                override fun onAddStream(p0: MediaStream) {}
                override fun onRemoveStream(p0: MediaStream) {}
                override fun onDataChannel(p0: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            }
        )!!

        peerConnection.addTrack(videoTrack)
    }

    private fun createOffer() {
        val constraints = MediaConstraints()

        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection.setLocalDescription(this, desc)
                println("SDP Offer:\n${desc.description}")
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }
}