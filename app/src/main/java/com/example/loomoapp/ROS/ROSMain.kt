package com.example.loomoapp.ROS

import android.os.Handler
import android.util.Log
import android.util.Pair
import com.example.loomoapp.Loomo.LoomoBase
import com.example.loomoapp.Loomo.LoomoRealSense
import com.example.loomoapp.Loomo.LoomoSensor
import org.ros.address.InetAddressFactory
import org.ros.android.RosActivity
import org.ros.message.Time
import org.ros.node.NodeConfiguration
import org.ros.node.NodeMainExecutor
import org.ros.time.NtpTimeProvider
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

//class ROSMain (base: LoomoBase, sensor: LoomoSensor, realSense: LoomoRealSense): RosActivity("LoomoROS", "LoomoROS", URI.create("http://192.168.2.31:11311/")) {
class ROSMain (handler: Handler, base: LoomoBase, sensor: LoomoSensor, realsense: LoomoRealSense): RosActivity("LoomoROS", "LoomoROS", URI.create("http://192.168.2.31:11311/")) {

    private val TAG = "RosMain"

    private val base_ = base
    private val sensor_ = sensor
    private val vision_ = realsense
    private val handler_ = handler

    // Keep track of timestamps when images published, so corresponding TFs can be published too
    // Stores a co-ordinated platform time and ROS time to help manage the offset
    private val mDepthRosStamps: Queue<Pair<Long, Time>> = ConcurrentLinkedDeque<Pair<Long, Time>>()
    private val mDepthStamps: Queue<Long> = ConcurrentLinkedDeque<Long>()

    //Rosbridge
    private lateinit var mOnNodeStarted: Runnable
    private lateinit var mOnNodeShutdown: Runnable
    private lateinit var mBridgeNode: RosBridgeNode

    //Publishers
    private val mRealsensePublisher = RealsensePublisher(mDepthStamps, mDepthRosStamps,vision_)
    private val mTFPublisher: TFPublisher = TFPublisher(mDepthStamps, mDepthRosStamps,base_,sensor_,vision_)
    private val mSensorPublisher: SensorPublisher = SensorPublisher(sensor_)
    private val mRosBridgeConsumers: List<RosBridge> =
        listOf(mRealsensePublisher, mTFPublisher, mSensorPublisher)

    fun initMain() {
        Log.d(TAG, "Ros Initialized ")
        // TODO: 25/02/2020 Not sure what these are used for
        mOnNodeStarted = Runnable {
            // Node has started, so we can now tell publishers and subscribers that ROS has initialized
            for (consumer in mRosBridgeConsumers) {
                consumer.node_started(mBridgeNode)
                // Try a call to start listening, this may fail if the Loomo SDK is not started yet (which is fine)
                consumer.start()
            }
        }

        // TODO: shutdown consumers correctly
        mOnNodeShutdown = Runnable { }

        // Start an instance of the RosBridgeNode
        mBridgeNode = RosBridgeNode()
        runOnUiThread{
            init()
        }
    }

    override fun init(nodeMainExecutor: NodeMainExecutor) {
        Log.d(TAG, "Init ROS node.")
        val nodeConfiguration = NodeConfiguration.newPublic(
            InetAddressFactory.newNonLoopback().hostAddress,
            masterUri
        )
        // Note: NTPd on Linux will, by default, not allow NTP queries from the local networks.
        // Add a rule like this to /etc/ntp.conf:
        //
        // restrict 192.168.86.0 mask 255.255.255.0 nomodify notrap nopeer
        //
        // Where the IP address is based on your subnet
        val ntpTimeProvider = NtpTimeProvider(
            InetAddressFactory.newFromHostString(masterUri.host),
            nodeMainExecutor.scheduledExecutorService
        )
        try {
            ntpTimeProvider.updateTime()
        } catch (e: Exception) {
            Log.d(TAG, "exception when updating time...")
        }
        Log.d(TAG, "master uri: " + masterUri.host)
        Log.d(TAG, "ros: " + ntpTimeProvider.currentTime.toString())
        Log.d(
            TAG,
            "sys: " + Time.fromMillis(System.currentTimeMillis())
        )
        ntpTimeProvider.startPeriodicUpdates(1, TimeUnit.MINUTES)
        nodeConfiguration.timeProvider = ntpTimeProvider
        nodeMainExecutor.execute(mBridgeNode, nodeConfiguration)
    }

    open fun startConsumers() {
        for (consumer in mRosBridgeConsumers) {
            consumer.node_started(mBridgeNode)
            // Try a call to start listening, this may fail if the Loomo SDK is not started yet (which is fine)
            consumer.start()
        }
    }

}