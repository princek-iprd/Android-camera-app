package com.example.androidwebrtcpublisher

import android.content.Context
import org.webrtc.*

class WebRTCManager(
    private val context: Context,
    private val localView: SurfaceViewRenderer,
    private var videoCapturer: CameraVideoCapturer? = null
) {

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var eglBase: EglBase
    private lateinit var videoSource: VideoSource
    private lateinit var videoTrack: VideoTrack
    private lateinit var peerConnection: PeerConnection

    private var isFrontCamera = true

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

    fun switchCamera() {
        videoCapturer?.let {
            if (it is CameraVideoCapturer) {
                it.switchCamera(null)
                isFrontCamera = !isFrontCamera
                println("🔄 Switched camera. Front = $isFrontCamera")
            }
        }
    }

    private fun startCamera() {

        videoCapturer = createCameraCapturer(isFrontCamera)

        if (videoCapturer == null) {
            println("❌ Camera capturer is NULL")
            return
        }

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

    private fun createCameraCapturer(front: Boolean): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)

        for (deviceName in enumerator.deviceNames) {
            if (front && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null) as CameraVideoCapturer
            }
            if (!front && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null) as CameraVideoCapturer
            }
        }

        return null
    }
}