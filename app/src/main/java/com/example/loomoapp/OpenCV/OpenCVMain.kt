package com.example.loomoapp.OpenCV

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.loomoapp.Loomo.LoomoRealSense.Companion.COLOR_HEIGHT
import com.example.loomoapp.Loomo.LoomoRealSense.Companion.COLOR_WIDTH
import com.example.loomoapp.Loomo.LoomoRealSense.Companion.DEPTH_HEIGHT
import com.example.loomoapp.Loomo.LoomoRealSense.Companion.DEPTH_WIDTH
import com.example.loomoapp.Loomo.LoomoRealSense.Companion.FISHEYE_HEIGHT
import com.example.loomoapp.Loomo.LoomoRealSense.Companion.FISHEYE_WIDTH
import com.example.loomoapp.utils.NonBlockingInfLoop
import com.example.loomoapp.utils.RingBuffer
import com.segway.robot.sdk.vision.frame.Frame
import com.segway.robot.sdk.vision.frame.FrameInfo
import com.segway.robot.sdk.vision.stream.StreamType
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.core.CvType.*
import org.opencv.imgproc.Imgproc

class OpenCVMain : Service() {
    private val TAG = "OpenCVMain"

    init {
        //Load native
//        System.loadLibrary("native-opencv")
//        System.loadLibrary("native-lib")
        //Load OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.d("$TAG init", "OpenCV not loaded")
        } else {
            Log.d("$TAG init", "OpenCV loaded")
        }
    }

    private lateinit var mLoaderCallback: BaseLoaderCallback

    private var fishEyeFrameBuffer = RingBuffer<Pair<Mat, FrameInfo>>(30, true)
    private var colorFrameBuffer = RingBuffer<Pair<Mat, FrameInfo>>(30, true)
    private var depthFrameBuffer = RingBuffer<Pair<Mat, FrameInfo>>(30, true)
    private var newFishEyeFrames = 0
    private var newColorFrames = 0
    private var newDepthFrames = 0

    private val fishEyeTracker = ORBTracker()
    var toggle = true

    override fun onBind(intent: Intent?): IBinder? {
        return Binder()
    }

    fun onCreate(context: Context) {
        mLoaderCallback = object : BaseLoaderCallback(context) {
            override fun onManagerConnected(status: Int) {
                when (status) {
                    LoaderCallbackInterface.SUCCESS -> Log.d(TAG, "OpenCV loaded successfully")
                    else -> super.onManagerConnected(status)
                }
            }
        }
    }

    fun resume() {
        //Start OpenCV
        Log.d(TAG, "Activity resumed")
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    //    private val imgProcFishEye = LoopedThread(
//        "imgProc FishEye",
//        Process.THREAD_PRIORITY_DEFAULT
//    )
//    private external fun nativeOrb(matAddr: Long, dstAddr: Long)
    var toggleOldState = false
    fun onNewFrame(streamType: Int, frame: Frame) {
//        val tic = System.currentTimeMillis()
        when (streamType) {
            StreamType.FISH_EYE -> {
                fishEyeFrameBuffer.enqueue(
                    Pair(
                        frame.byteBuffer.toMat(
                            FISHEYE_WIDTH, FISHEYE_HEIGHT,
                            CV_8UC1
                        ), frame.info
                    )
                )
                ++newFishEyeFrames
            }
            StreamType.COLOR -> {
                colorFrameBuffer.enqueue(
                    Pair(
                        frame.byteBuffer.toMat(
                            COLOR_WIDTH, COLOR_HEIGHT,
                            CV_8UC4
                        ), frame.info
                    )
                )
                ++newColorFrames
            }
            StreamType.DEPTH -> {
                depthFrameBuffer.enqueue(
                    Pair(
                        frame.byteBuffer.toMat(
                            DEPTH_WIDTH, DEPTH_HEIGHT,
//                            CV_16UC1
                            CV_8UC2
                        ), frame.info
                    )
                )
                ++newDepthFrames
            }
            else -> {
                throw IllegalStreamTypeException("Stream type not recognized in onNewFrame")
            }
        }
        if (toggleOldState != toggle) {
            toggleOldState = toggle
            Log.d(
                TAG,
                "Fisheye Mat() type: ${typeToString(fishEyeFrameBuffer.peek()!!.first.type())}"
            )
            Log.d(TAG, "Color Mat() type: ${typeToString(colorFrameBuffer.peek()!!.first.type())}")
            Log.d(TAG, "Depth Mat() type: ${typeToString(depthFrameBuffer.peek()!!.first.type())}")
        }
//        val toc = System.currentTimeMillis()
//        Log.d(TAG, "${streamTypeMap[streamType]} frame receive time: ${toc - tic}ms")
    }


    fun getNewestFrame(streamType: Int, callback: (Bitmap) -> Unit) {
        val frame: Mat? = when (streamType) {
//            StreamType.FISH_EYE -> fishEyeFrameBuffer.peek(1)?.first
            StreamType.FISH_EYE -> {
                if (toggle) {
//                    processedFishEyeFrame
                    drawStuff(fishEyeFrame)
                }
                else fishEyeFrameBuffer.peek(1)?.first
            }
            StreamType.COLOR -> colorFrameBuffer.peek(1)?.first
            StreamType.DEPTH -> depthFrameBuffer.peek(1)?.first
            else -> throw IllegalStreamTypeException("Non recognized stream type in getNewestFrame()")
        }
        if (frame == null) {
            callback(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        } else {
            callback(frame.toBitmap())
        }
    }

    private var keyPoints = MatOfKeyPoint()
    private var pointPair = Pair<MatOfPoint2f, MatOfPoint2f>(MatOfPoint2f(), MatOfPoint2f())
    private var fishEyeFrame = Mat()
    private var processedFishEyeFrame = Mat()
    private val foo = NonBlockingInfLoop {
        if (newFishEyeFrames > 0) {
//            Log.d(TAG, "Skipped frames: ${newFishEyeFrames-1}")
        newFishEyeFrames = 0
        fishEyeFrame = fishEyeFrameBuffer.peek()!!.first
//            keyPoints = fishEyeTracker.onNewFrame(fishEyeFrame)
//            Features2d.drawKeypoints(fishEyeFrame, keyPoints, processedFishEyeFrame, Scalar(0.0, 255.0, 0.0))
        pointPair = fishEyeTracker.onNewFrame(fishEyeFrame)
//            val pointOld = pointPair.first.toArray()
//            val point = pointPair.second.toArray()
//            if ((point.isNotEmpty()) and (pointOld.size == point.size)) {
//                Log.d(TAG, "Drawing lines")
//                Imgproc.line(fishEyeFrame, pointOld[0], point[0], Scalar(0.0, 0.0, 255.0), 2)
//                Imgproc.line(fishEyeFrame, Point(10.0, 10.0), Point(100.0, 100.0), Scalar(0.0, 0.0, 255.0), 2)
//                for (index in point.indices) {
//                    Imgproc.line(
//                        fishEyeFrame,
//                        pointOld[index]!!,
//                        point[index]!!,
//                        Scalar(0.0, 0.0, 255.0),
//                        1
//                    )
//                }
        }
//            processedFishEyeFrame = fishEyeFrame
//            Thread.sleep(2000) // Just for debugging purposes
    }

    private fun drawStuff(frame: Mat): Mat {
        val pointOld = pointPair.first.toArray()
        val point = pointPair.second.toArray()
        if ((point.isNotEmpty()) and (pointOld.size == point.size)) {
            Log.d(TAG, "Drawing lines")
//            Imgproc.line(fishEyeFrame, pointOld[0], point[0], Scalar(0.0, 0.0, 255.0), 2)
            Imgproc.line(frame, Point(10.0, 10.0), Point(100.0, 100.0), Scalar(0.0, 0.0, 255.0))
//            for (index in point.indices) {
//                Imgproc.line(
//                    img,
//                    pointOld[index],
//                    point[index],
//                    Scalar(0.0, 0.0, 255.0),
//                    1
//                )
//            }
        }
        return frame
    }

}

class IllegalStreamTypeException(msg: String) : RuntimeException(msg)

//adb logcat | C:\Users\ja_ei\AppData\Local\Android\Sdk\ndk\21.0.6113669\ndk-stack -sym C:\OpenCVAndroidSDK\sdk\build\intermediates\cmake\debug\obj\x86_64