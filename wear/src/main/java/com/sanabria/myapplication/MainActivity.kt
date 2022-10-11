package com.sanabria.myapplication

import android.R.attr.bitmap
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.ExecutionException


/**
 * Wear Device code  so I can kept this straight.
 *
 * This will receive messages (from a device/phone) via the datalayer (through the listener code)
 * and display them to the wear device.  There is also a button to send a message
 * to the device/phone as well.
 *
 * if the wear device receives a message from the phone/device it will then send a message back
 * via the button on the wear device, it can also send a message to the device/phone as well.
 * There is no auto response from the phone/device otherwise we would get caught in a loop!
 *
 * debuging over bluetooth.
 * https://developer.android.com/training/wearables/apps/debugging.html
 */

class MainActivity : Activity() {
    private val TAG = "Wear MainActivity"
    private var clock: TextClock? = null
    private var play: Button? = null
    private var prev: Button? = null
    private var next: Button? = null
    private var track: TextView? = null
    private var artist: TextView? = null
    private var background: ImageView? = null

    var datapath = "/message_path"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var message: String
        clock?.setTypeface(null, Typeface.BOLD)
        background = findViewById(R.id.coverArt)
        track = findViewById(R.id.track)
        artist = findViewById(R.id.artist)
        play = findViewById(R.id.play)
        play!!.setOnClickListener {
            message = "play"
            //Requires a new thread to avoid blocking the UI
            SendThread(datapath, message).start()
        }
        prev = findViewById(R.id.prev)
        prev!!.setOnClickListener {
            message = "prev"
            SendThread(datapath, message).start()
        }
        next = findViewById(R.id.next)
        next!!.setOnClickListener {
            message = "next"
            SendThread(datapath, message).start()
        }
        // Register the local broadcast receiver to receive messages from the listener.
        val messageFilter = IntentFilter(Intent.ACTION_SEND)
        val messageReceiver = MessageReceiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter)

    }

    inner class MessageReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            var message = intent.getStringExtra("message")
            Log.v(
                TAG, "Main activity received message: $message"
            )
            if (message.equals("toggled")) toggled()
            else if (message.equals("next")) toggled()
            else if (message.equals("prev")) toggled()
            else {
                if (message != null && message.length < 400) {
                    artist?.text = message.split("//////")[1]
                    track?.text = message.split("//////")[0]
                }else{
                    val coverArt = StringToBitMap(message)
                    val mDrawable: Drawable = BitmapDrawable(resources, coverArt)
                    background?.background = mDrawable
                }
            }
        }
    }

    //This actually sends the message to the wearable device.
    internal inner class SendThread     //constructor
        (var path: String, var message: String) : Thread() {
        //sends the message via the thread.  this will send to all wearables connected, but
        //since there is (should only?) be one, so no problem.
        override fun run() {
            //first get all the nodes, ie connected wearable devices.
            val nodeListTask = Wearable.getNodeClient(applicationContext).connectedNodes
            try {
                // Block on a task and get the result synchronously (because this is on a background
                // thread).
                val nodes = Tasks.await(nodeListTask)
                //Now send the message to each device.
                for (node in nodes) {
                    val sendMessageTask = Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(node.id, path, message.toByteArray())
                    try {
                        // Block on a task and get the result synchronously (because this is on a background
                        // thread).
                        val result = Tasks.await(sendMessageTask)
                        Log.v(TAG, "SendThread: message send to " + node.displayName)
                    } catch (exception: ExecutionException) {
                        Log.e(TAG, "Task failed: $exception")
                    } catch (exception: InterruptedException) {
                        Log.e(TAG, "Interrupt occurred: $exception")
                    }
                }
            } catch (exception: ExecutionException) {
                Log.e(TAG, "Task failed: $exception")
            } catch (exception: InterruptedException) {
                Log.e(TAG, "Interrupt occurred: $exception")
            }
        }
    }

    fun StringToBitMap(encodedString: String?): Bitmap? {
        return try {
            val encodeByte: ByteArray = Base64.decode(encodedString, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.size)
        } catch (e: java.lang.Exception) {
            e.message
            null
        }
    }

    fun toggled() {
        TODO()
    }
}