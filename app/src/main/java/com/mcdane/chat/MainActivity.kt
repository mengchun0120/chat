package com.mcdane.chat

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.opengl.Visibility
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class MainActivity : AppCompatActivity() {
    private lateinit var localIPText: TextView
    private lateinit var statusText: TextView
    private lateinit var runAsServerButton: Button
    private lateinit var connectToServerButton: Button
    private lateinit var connectButtons: LinearLayout
    private lateinit var msgList: RecyclerView
    private lateinit var msgEdit: EditText
    private lateinit var sendButton: Button
    private var localIP: InetAddress? = null
    private var remoteIP: InetAddress? = null
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var serverThread = Thread{ runServer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initUI()
        initLocalIPAddress()
    }

    private fun initUI() {
        localIPText = findViewById(R.id.local_ip)
        statusText = findViewById(R.id.status)
        runAsServerButton = findViewById(R.id.run_as_server_button)
        runAsServerButton.setOnClickListener{ onRunAsServerClicked() }
        connectToServerButton = findViewById(R.id.connect_to_server_button)
        connectToServerButton.setOnClickListener{ onConnectToServerClicked() }
        connectButtons = findViewById(R.id.connect_buttons)
        msgList = findViewById(R.id.msg_list)
        msgEdit = findViewById(R.id.msg_edit)
        sendButton = findViewById(R.id.send_button)
        sendButton.setOnClickListener{ onSendClicked() }
    }

    private fun initLocalIPAddress() {
        localIP = findLocalIPv4(this)
        localIPText.text = localIP?.hostAddress ?: "Unknonw network"
        runAsServerButton.setEnabled(localIP != null)
        connectToServerButton.setEnabled(localIP != null)
    }

    private fun onRunAsServerClicked() {
        statusText.setText(R.string.wait_for_client)
        serverThread.start()
    }

    private fun onConnectToServerClicked() {
        Log.i(TAG, "Connect to server clicked")
    }

    private fun onSendClicked() {
        Log.i(TAG, "Send clicked")
    }

    private fun runServer() {
        try {
            serverSocket = ServerSocket(PORT, 1, localIP)
            Log.i(TAG, "serverSocket created")
            socket = serverSocket?.accept()
            Log.i(TAG, "client socket accepted")
            remoteIP = socket?.inetAddress
            onConnectionSuccess()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Exception happened: ${e.localizedMessage}\n${e.stackTrace}")
            onConnectionFail()
        }
    }

    private fun onConnectionSuccess() {
        runOnUiThread {
            statusText.text = "Connected to ${remoteIP?.hostAddress}"
            connectButtons.visibility = View.GONE
            sendButton.setEnabled(true)
        }
    }

    private fun onConnectionFail() {
        runOnUiThread {
            statusText.text = "Network failure"
        }
    }

    companion object {
        const val TAG = "MainActivity"
        const val PORT = 8999
    }
}