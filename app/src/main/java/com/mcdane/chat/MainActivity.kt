package com.mcdane.chat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
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
import java.nio.CharBuffer
import java.sql.Date
import java.util.concurrent.atomic.AtomicBoolean

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
        val records = ArrayList<ChatRecord>()

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

        override fun getItemCount(): Int {
            Log.i(TAG, "getItemCount ${records.size}")
            return records.size
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            with(holder) {
                nameText.text = records[position].name
                msgText.text = records[position].msg
                timeText.text = records[position].time.toString()
                Log.i(TAG, "onBind $position")
            }
        }

        override fun getItemViewType(position: Int): Int =
            if (records[position].local) LOCAL_VIEW else REMOTE_VIEW

        companion object {
            const val LOCAL_VIEW = 0
            const val REMOTE_VIEW = 1
        }
    }

    private val handlerThread = HandlerThread("chat").apply{ start() }
    private val handler = Handler(handlerThread.looper)
    private lateinit var localIPText: TextView
    private lateinit var statusText: TextView
    private lateinit var runAsServerButton: Button
    private lateinit var connectToServerButton: Button
    private lateinit var discconectButton: Button
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
    private val running = AtomicBoolean()

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

        discconectButton = findViewById(R.id.disconnect_button)
        discconectButton.setOnClickListener{ disconnect() }

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
        val msg = msgEdit.text.toString()
        if (!msg.isEmpty()) {
            handler.post{ sendMsg(msg) }
        }
    }

    private fun startServer() {
        statusText.setText(R.string.wait_for_client)
        setRunAsServerButton(true)
        connectToServerButton.setEnabled(false)
        handler.post{ runServer() }
    }

    private fun runServer() {
        try {
            serverSocket = ServerSocket(PORT, 1, localIP)
            socket = serverSocket?.accept()
            remoteIP = socket?.inetAddress
            writer = PrintWriter(socket?.getOutputStream()!!)
            startReaderThread()
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
        setRunAsServerButton(false)
        connectToServerButton.setEnabled(true)
    }

    private fun promptServer() {
        runOnUiThread {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.enter_server_address)

                val view = layoutInflater.inflate(R.layout.get_server_ip, null)
                setView(view)

                val ipText = view.findViewById<EditText>(R.id.server_ip)

                setPositiveButton(R.string.ok) { dialog, _ ->
                    ipText.text.toString().trim().apply {
                        if (validateIP(this)) {
                            startClient(this)
                            dialog.dismiss()
                        }
                    }
                }

                setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
            }.show()
        }
    }

    private fun startClient(serverIP: String) {
        statusText.setText(R.string.wait_for_server)
        setConnectToServerButton(true)
        runAsServerButton.setEnabled(false)
        handler.post{ runClient(serverIP) }
    }

    private fun runClient(serverIP: String) {
        try {
            socket = Socket(serverIP, PORT)
            remoteIP = socket?.inetAddress
            writer = PrintWriter(socket?.getOutputStream()!!)
            startReaderThread()
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
        setConnectToServerButton(false)
        runAsServerButton.setEnabled(true)
    }

    private fun startReaderThread() {
        Thread {
            Log.i(TAG, "readerThread started")
            try {
                running.set(true)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()!!))
                while (running.get()) {
                    reader?.apply {
                        val msg = readLine()
                        Log.i(TAG, "Received $msg")
                        addMsg(msg, false)
                    }
                }
            } catch(e: Exception) {
                Log.e(TAG, "Exception happened: ${e.localizedMessage}")
                onConnectionFail(e.localizedMessage ?: "Unknown exception")
            }
        }.start()
    }

    private fun addMsg(msg: String, local: Boolean) {
        runOnUiThread {
            adapter.records.add(
                ChatRecord(name(local), msg, local, Date(System.currentTimeMillis()))
            )
            Log.i(TAG, "records: ${adapter.records.size}")
            adapter.notifyItemInserted(adapter.records.size)
        }
    }

    private fun sendMsg(msg: String) {
        try {
            writer?.apply {
                Log.i(TAG, "Sending $msg")
                println(msg)
                flush()
            }
            addMsg(msg, true)
        } catch (e: Exception) {
            Log.e(TAG, "Exception happened: ${e.localizedMessage}")
            onConnectionFail(e.localizedMessage ?: "Unknown exception")
        }
    }

    private fun name(local: Boolean) =
        (if (local) localIP else remoteIP) ?.hostAddress ?: ""

    private fun disconnect() {

    }

    private fun onConnectionSuccess() {
        runOnUiThread {
            statusText.text = "Connected to ${remoteIP?.hostAddress}"
            msgEdit.setEnabled(true)
            sendButton.setEnabled(true)
            runAsServerButton.visibility = GONE
            connectToServerButton.visibility = GONE
            discconectButton.visibility = VISIBLE
        }
    }

    private fun onConnectionFail(msg: String) {
        runOnUiThread {
            statusText.text = msg
            runAsServerButton.visibility = VISIBLE
            connectToServerButton.visibility = VISIBLE
            discconectButton.visibility = GONE
        }
    }

    private fun validateIP(ipStr: String): Boolean =
        try {
            var partCount = 0
            for (part in ipStr.splitToSequence(".")) {
                if (part.toInt() !in 0..255) {
                    throw RuntimeException("Invalid IP")
                }
                ++partCount
            }
            if (partCount != 4) {
                throw RuntimeException("Invalid IP")
            }
            true
        } catch (e: Exception) {
            Log.i(TAG, "Invalid IP")
            Toast.makeText(this, R.string.invalid_ip, Toast.LENGTH_SHORT).show()
            false
        }

    companion object {
        const val TAG = "MainActivity"
        const val PORT = 8999
        const val MAX_MSG_LEN=200
    }
}