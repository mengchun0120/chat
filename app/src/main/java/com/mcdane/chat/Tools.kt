package com.mcdane.chat

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import java.net.InetAddress

fun findLocalIPv4(context: Context): InetAddress? =
    context.getSystemService(ConnectivityManager::class.java).run {
        getLinkProperties(activeNetwork)
    }?.linkAddresses?.find { it.address.address.size == 4 }?.address