package com.mcdane.chat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.sql.Date

class MainActivity : AppCompatActivity() {
    data class ChatRecord(
        val name: String, val msg: String, val local: Boolean, val time: Date
    )

    class ChatViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.name)
        val msgText: TextView = view.findViewById(R.id.msg)
        val timeText: TextView = view.findViewById(R.id.time)
    }

    class ChatAdapter: RecyclerView.Adapter<ChatViewHolder>() {
        val records = arrayListOf(
            ChatRecord("Michael", "This is me flying to your place", true, Date(System.currentTimeMillis())),
            ChatRecord("Mira", "Hold it tight, I will be there", false, Date(System.currentTimeMillis())),
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val layout = if (viewType == LOCAL_VIEW) {
                R.layout.local_chat_record
            } else {
                R.layout.remote_chat_record
            }
            val view = inflater.inflate(layout, parent, false)
            return ChatViewHolder(view)
        }

        override fun getItemCount(): Int = records.size

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            with(holder) {
                nameText.text = records[position].name
                msgText.text = records[position].msg
                timeText.text = records[position].time.toString()
            }
        }

        override fun getItemViewType(position: Int): Int =
            if (records[position].local) LOCAL_VIEW else REMOTE_VIEW

        companion object {
            const val LOCAL_VIEW = 0
            const val REMOTE_VIEW = 1
        }
    }

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
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var adapter = ChatAdapter()

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
        setRunAsServerButton(false)
        connectToServerButton = findViewById(R.id.connect_to_server_button)
        setConnectToServerButton(false)
        connectButtons = findViewById(R.id.connect_buttons)
        msgList = findViewById(R.id.msg_list)
        msgList.adapter = adapter
        msgList.layoutManager = LinearLayoutManager(this)
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

    private fun setRunAsServerButton(showStop: Boolean) {
        with(runAsServerButton) {
            if (showStop) {
                setText(R.string.stop_server)
                setOnClickListener{ stopServer() }
            } else {
                setText(R.string.run_as_server)
                setOnClickListener{ startServer() }
            }
        }
    }

    private fun setConnectToServerButton(showStop: Boolean) {
        with(connectToServerButton) {
            if (showStop) {
                setText(R.string.stop_connect)
                setOnClickListener{ stopClient() }
            } else {
                setText(R.string.connect_to_server)
                setOnClickListener{ promptServer() }
            }
        }
    }

    private fun onSendClicked() {
        Log.i(TAG, "Send clicked")
    }

    private fun startServer() {
        statusText.setText(R.string.wait_for_client)
        setRunAsServerButton(true)
        connectToServerButton.setEnabled(false)
        Thread{ runServer() }.start()
    }

    private fun runServer() {
        try {
            serverSocket = ServerSocket(PORT, 1, localIP)
            socket = serverSocket?.accept()
            remoteIP = socket?.inetAddress
            writer = PrintWriter(socket?.getOutputStream()!!)
            reader = BufferedReader(InputStreamReader(socket?.getInputStream()!!))
            onConnectionSuccess()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Exception happened: ${e.localizedMessage}")
            onConnectionFail(e.localizedMessage ?: "Unknown exception")
        }
    }

    private fun stopServer() {
        serverSocket?.let{
            if (!it.isClosed) it.close()
        }
    }

    private fun promptServer() {
        runOnUiThread {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.enter_server_address)

                val view = layoutInflater.inflate(R.layout.get_server_ip_port, null)
                setView(view)

                val ipText = view.findViewById<EditText>(R.id.server_ip)
                val portText = view.findViewById<EditText>(R.id.server_port)

                setPositiveButton(R.string.ok) { dialog, _ ->
                    val ipStr = ipText.text.toString()
                    val portStr = portText.text.toString()
                    if (validateIP(ipStr) && validatePort(portStr)) {
                        startClient(ipStr, portStr.toInt())
                        dialog.dismiss()
                    }
                }
                setNegativeButton(R.string.cancel) { dialog, _ ->
                    Log.i(TAG, "Cancel clicked")
                    dialog.dismiss()
                }
            }.show()
        }
    }

    private fun startClient(serverIP: String, port: Int) {
        Log.i(TAG, "startClient $serverIP $port")
        statusText.setText(R.string.wait_for_server)
        setConnectToServerButton(true)
        runAsServerButton.setEnabled(false)
        Thread{ runClient(serverIP, port) }.start()
    }

    private fun runClient(serverIP: String, port: Int) {
        try {
            socket = Socket(serverIP, port)
            remoteIP = socket?.inetAddress
            writer = PrintWriter(socket?.getOutputStream()!!)
            reader = BufferedReader(InputStreamReader(socket?.getInputStream()!!))
            onConnectionSuccess()
        } catch(e: Exception) {
            Log.e(TAG, "Exception happened: ${e.localizedMessage}")
            onConnectionFail(e.localizedMessage ?: "Unknown exception")
        }
    }

    private fun stopClient() {
        socket?.let {
            if (!it.isClosed) it.close()
        }
    }

    private fun onConnectionSuccess() {
        runOnUiThread {
            statusText.text = "Connected to ${remoteIP?.hostAddress}"
            connectButtons.visibility = View.GONE
            msgEdit.setEnabled(true)
            sendButton.setEnabled(true)
        }
    }

    private fun onConnectionFail(msg: String) {
        runOnUiThread {
            statusText.text = msg
        }
    }

    private fun validateIP(ipStr: String): Boolean =
        try {
            Log.i(TAG, "'$ipStr'")
            var partCount = 0
            for (part in ipStr.splitToSequence(".")) {
                Log.i(TAG, "$partCount:$part")
                if (part.toInt() !in 0..255) {
                    Log.i(TAG, "part INVALID")
                    throw RuntimeException("Invalid IP")
                }
                ++partCount
            }
            if (partCount != 4) {
                Log.i(TAG, "partCount=${partCount}")
                throw RuntimeException("Invalid IP")
            }
            true
        } catch (e: Exception) {
            Log.i(TAG, "Invalid IP")
            Toast.makeText(this, R.string.invalid_ip, Toast.LENGTH_SHORT).show()
            false
        }

    private fun validatePort(portStr: String): Boolean =
        try {
            if (portStr.toInt() <= 0) {
                throw RuntimeException("Invalid port")
            }
            true
        } catch (e: Exception) {
            Log.i(TAG, "Invalid port")
            Toast.makeText(this, R.string.invalid_port, Toast.LENGTH_SHORT).show()
            false
        }

    companion object {
        const val TAG = "MainActivity"
        const val PORT = 8999
    }
}