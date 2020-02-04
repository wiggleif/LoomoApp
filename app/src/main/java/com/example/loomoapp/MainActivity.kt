package com.example.loomoapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.renderscript.Sampler
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.loomoapp.viewModel.ASDFViewModel
import com.segway.robot.sdk.base.bind.ServiceBinder
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: ASDFViewModel

    private val textView by lazy {
        findViewById<TextView>(R.id.textView)
    }

    var myService : ValueReader? = null
    var isBound = false

    private var myConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName,
                                                    service: IBinder) {
            val binder = service as ValueReader.LocalBinder
            myService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        Log.i("asd", "Activity created")

        btnStartService.setOnClickListener {
            startService()
        }
        btnStopService.setOnClickListener {
            stopService()
        }

        viewModel = ViewModelProvider(this)
            .get(ASDFViewModel::class.java)

        viewModel.text.observe(this, Observer {
            textView.text= it
        })
    }

    private fun startService(){
        Log.i("asd", "Service start command")
        val intent = Intent(this, ValueReader::class.java)
        startService(intent)
//        bindService(intent, myConnection, Context.BIND_AUTO_CREATE)

    }

    private fun stopService (){
        Log.i("asd", "Service stop command")
        val intent = Intent(this, ValueReader::class.java)
        stopService(intent)
//        textView.text= "service stopped"
    }
}




