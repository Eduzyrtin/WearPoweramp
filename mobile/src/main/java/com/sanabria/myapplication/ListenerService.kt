package com.sanabria.myapplication

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/*
 *  This is a listener on the device to get messages via
 *  the datalayer and then pass it to the main activity so it can be
 *  displayed. The messages should be coming from the wear/watch device.
*/
class ListenerService : WearableListenerService() {
    var TAG = "mobile Listener"
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/message_path") {
            val message = String(messageEvent.data)
            Log.v(TAG, "Message path received on phone is: " + messageEvent.path)
            Log.v(TAG, "Message received on phone is: $message")

            // Broadcast message to MainActivity for display
            val messageIntent = Intent()
            messageIntent.action = Intent.ACTION_SEND
            messageIntent.putExtra("message", message)
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent)
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}