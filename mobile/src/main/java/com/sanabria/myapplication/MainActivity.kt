/*
Copyright (C) 2011-2021 Maksim Petrov

Redistribution and use in source and binary forms, with or without
modification, are permitted for widgets, plugins, applications and other software
which communicate with Poweramp application on Android platform.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.sanabria.myapplication

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.os.PersistableBundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.sanabria.myapplication.powerampAPI.player.PowerampAPI
import com.sanabria.myapplication.powerampAPI.player.PowerampAPIHelper
import com.sanabria.myapplication.powerampAPI.player.RemoteTrackTime
import com.sanabria.myapplication.powerampAPI.player.RemoteTrackTime.TrackTimeListener
import com.sanabria.myapplication.powerampAPI.player.TableDefs
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException


class MainActivity : AppCompatActivity(), View.OnClickListener, OnLongClickListener,
    OnTouchListener, CompoundButton.OnCheckedChangeListener, OnSeekBarChangeListener,
    AdapterView.OnItemSelectedListener, TrackTimeListener {
    protected var mTrackIntent: Intent? = null
    private var mStatusIntent: Intent? = null
    protected var mPlayingModeIntent: Intent? = null
    private var mCurrentTrack: Bundle? = null
    private var mRemoteTrackTime: RemoteTrackTime? = null
    private var mSongSeekBar: SeekBar? = null
    private var mDuration: TextView? = null
    private var mElapsed: TextView? = null
    private var mSettingPreset = false
    private var mLastSeekSentTime: Long = 0
    private val mDurationBuffer = StringBuilder()
    private val mElapsedBuffer = StringBuilder()

    @Nullable
    private var mLastCreatedPlaylistFilesUri: Uri? = null

    var datapath = "/message_path"
    protected var handler: Handler? = null
    var TAG = "Mobile MainActivity"
    var logger: TextView? = null

    /**
     * Use getPowerampBuildNumber to get the build number
     */
    private var mPowerampBuildNumber = 0
    private var mProcessingLongPress = false
    private var mLastSentSeekPosition = 0
    public override fun onCreate(savedInstanceState: Bundle?) {
        if (LOG_VERBOSE) Log.w(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.play).setOnClickListener(this)
        findViewById<View>(R.id.play).setOnLongClickListener(this)
        findViewById<View>(R.id.pause).setOnClickListener(this)
        findViewById<View>(R.id.prev).setOnClickListener(this)
        findViewById<View>(R.id.prev).setOnLongClickListener(this)
        findViewById<View>(R.id.prev).setOnTouchListener(this)
        findViewById<View>(R.id.next).setOnClickListener(this)
        findViewById<View>(R.id.next).setOnLongClickListener(this)
        findViewById<View>(R.id.next).setOnTouchListener(this)
        findViewById<View>(R.id.prev_in_cat).setOnClickListener(this)
        findViewById<View>(R.id.next_in_cat).setOnClickListener(this)
        findViewById<View>(R.id.repeat).setOnClickListener(this)
        findViewById<View>(R.id.shuffle).setOnClickListener(this)
        findViewById<View>(R.id.repeat_all).setOnClickListener(this)
        findViewById<View>(R.id.repeat_off).setOnClickListener(this)
        findViewById<View>(R.id.shuffle_all).setOnClickListener(this)
        findViewById<View>(R.id.shuffle_off).setOnClickListener(this)
        mSongSeekBar = findViewById(R.id.song_seekbar)
        mSongSeekBar?.setOnSeekBarChangeListener(this)
        mDuration = findViewById(R.id.duration)
        mElapsed = findViewById(R.id.elapsed)
        mRemoteTrackTime = RemoteTrackTime(this)
        mRemoteTrackTime!!.setTrackTimeListener(this)

        //((TextView)findViewById(R.id.play_file_path)).setText(findFirstMP3(Environment.getExternalStorageDirectory())); // This can be slow, disabled
        findViewById<View>(R.id.play_file).setOnClickListener(this)
        findViewById<View>(R.id.play_album).setOnClickListener(this)
        findViewById<View>(R.id.play_all_songs).setOnClickListener(this)
        findViewById<View>(R.id.play_second_artist_first_album).setOnClickListener(this)
        findViewById<View>(R.id.pa_current_list).setOnClickListener(this)
        findViewById<View>(R.id.pa_folders).setOnClickListener(this)
        findViewById<View>(R.id.pa_all_songs).setOnClickListener(this)
        (findViewById<View>(R.id.sleep_timer_seekbar) as SeekBar).setOnSeekBarChangeListener(this)

        // Ask Poweramp for a permission to access its data provider. Needed only if we want to make queries against Poweramp database, e.g. in FilesActivity/FoldersActivity
        // NOTE: this will work only if Poweramp process is alive.
        // This actually should be done once per this app installation, but for the simplicity, we use per-process static field here
        if (!sPermissionAsked) {
            if (LOG_VERBOSE) Log.w(TAG, "onCreate skin permission")
            val intent = Intent(PowerampAPI.ACTION_ASK_FOR_DATA_PERMISSION)
            intent.setPackage(PowerampAPIHelper.getPowerampPackageName(this))
            intent.putExtra(PowerampAPI.EXTRA_PACKAGE, packageName)
            if (FORCE_API_ACTIVITY) {
                intent.component = PowerampAPIHelper.getApiActivityComponentName(this)
                startActivity(intent)
            } else {
                sendBroadcast(intent)
            }
            sPermissionAsked = true
        }
        componentNames
        if (LOG_VERBOSE) Log.w(TAG, "onCreate DONE")


        //DataLayer
        handler = Handler { msg ->
            val stuff = msg.data
            //logthis(stuff.getString("logthis"))
            true
        }
        // Register the local broadcast receiver
        val messageFilter = IntentFilter(Intent.ACTION_SEND)
        val messageReceiver = MessageReceiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter)
    }

    //setup a broadcast receiver to receive the messages from the wear device via the listenerService.
    inner class MessageReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getStringExtra("message")
            Log.v(TAG, "Main activity received message: $message")
            if (message.equals("play")) play()
            if (message.equals("next")) next()
            if (message.equals("prev")) prev()
        }
    }


    //method to create up a bundle to send to a handler via the thread below.
    fun sendMessage(logthis: String?) {
        val b = Bundle()
        b.putString("logthis", logthis)
        val msg = handler!!.obtainMessage()
        msg.data = b
        msg.arg1 = 1
        msg.what = 1 //so the empty message is not used!
        handler!!.sendMessage(msg)
    }

    //This actually sends the message to the wearable device.
    internal inner class SendThread     //constructor
        (var path: String, var message: String) : Thread() {
        //sends the message via the thread.  this will send to all wearables connected, but
        //since there is (should only?) be one, no problem.
        override fun run() {
            //first get all the nodes, ie connected wearable devices.
            val nodeListTask = Wearable.getNodeClient(
                applicationContext
            ).connectedNodes
            try {
                // Block on a task and get the result synchronously (because this is on a background
                // thread).
                val nodes = Tasks.await(nodeListTask)
                //Now send the message to each device.
                for (node in nodes) {
                    val sendMessageTask = Wearable.getMessageClient(applicationContext)
                        .sendMessage(node.id, path, message.toByteArray())
                    try {
                        // Block on a task and get the result synchronously (because this is on a background
                        // thread).
                        val result = Tasks.await(sendMessageTask)
                        sendMessage("SendThread: message send to " + node.displayName)
                        Log.v(TAG, "SendThread: message send to " + node.displayName)
                    } catch (exception: ExecutionException) {
                        sendMessage("SendThread: message failed to" + node.displayName)
                        Log.e(TAG, "Send Task failed: $exception")
                    } catch (exception: InterruptedException) {
                        Log.e(TAG, "Send Interrupt occurred: $exception")
                    }
                }
            } catch (exception: ExecutionException) {
                sendMessage("Node Task failed: $exception")
                Log.e(TAG, "Node Task failed: $exception")
            } catch (exception: InterruptedException) {
                Log.e(TAG, "Node Interrupt occurred: $exception")
            }
        }
    }

    /**
     * When screen is rotated, by default Android will reapply all saved values to the controls, calling the event handlers, which generate appropriate intents, thus
     * on screen rotation some commands could be sent to Poweramp unintentionally.
     * As this activity always syncs everything with the actual state of Poweramp, the automatic restoring of state is non needed and harmful.
     * <br></br><br></br>
     * Nevertheless, the actual implementation should probably manipulate per view View.setSaveEnabled() for specific controls, use some Model pattern, or manage
     * state otherwise, as empty onSaveInstanceState here denies save for everything
     */
    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {}

    /**
     * @see .onSaveInstanceState
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {}

    /**
     * This method unregister all broadcast receivers on activity pause. This is the correct way of handling things - we're
     * sure no unnecessary event processing will be done for paused activity, when screen is OFF, etc.
     * Alternatively, we may do this in onStop/onStart, esp. for latest Android versions and things like split screen
     */
    override fun onPause() {
        unregister()
        mRemoteTrackTime!!.unregister()
        super.onPause()
    }

    /**
     * Register broadcast receivers
     */
    override fun onResume() {
        super.onResume()
        registerAndLoadStatus()
        mRemoteTrackTime!!.registerAndLoadStatus()
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy")
        try {
            unregister()
            mRemoteTrackTime!!.setTrackTimeListener(null)
            mRemoteTrackTime!!.unregister()
            mRemoteTrackTime = null
            mTrackReceiver = null
            mStatusReceiver = null
            mPlayingModeReceiver = null
        } catch (ex: Exception) {
            Log.e(TAG, "", ex)
        }
        super.onDestroy()
    }

    /**
     * NOTE: it's not necessary to set mStatusIntent/mPlayingModeIntent this way here,
     * but this approach can be used with a null receiver to get current sticky intent without broadcast receiver.
     */
    private fun registerAndLoadStatus() {
        mTrackIntent =
            registerReceiver(mTrackReceiver, IntentFilter(PowerampAPI.ACTION_TRACK_CHANGED))
        mStatusIntent =
            registerReceiver(mStatusReceiver, IntentFilter(PowerampAPI.ACTION_STATUS_CHANGED))
        mPlayingModeIntent = registerReceiver(
            mPlayingModeReceiver, IntentFilter(PowerampAPI.ACTION_PLAYING_MODE_CHANGED)
        )
        registerReceiver(
            mMediaButtonIgnoredReceiver, IntentFilter(PowerampAPI.ACTION_MEDIA_BUTTON_IGNORED)
        )
    }

    private fun unregister() {
        if (mTrackIntent != null) {
            try {
                unregisterReceiver(mTrackReceiver)
            } catch (ignored: Exception) {
            } // Can throw exception if for some reason broadcast receiver wasn't registered.
        }
        if (mStatusReceiver != null) {
            try {
                unregisterReceiver(mStatusReceiver)
            } catch (ignored: Exception) {
            }
        }
        if (mPlayingModeReceiver != null) {
            try {
                unregisterReceiver(mPlayingModeReceiver)
            } catch (ignored: Exception) {
            }
        }
        if (mMediaButtonIgnoredReceiver != null) {
            try {
                unregisterReceiver(mMediaButtonIgnoredReceiver)
            } catch (ignored: Exception) {
            }
        }
    }

    private var mTrackReceiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mTrackIntent = intent
            processTrackIntent()
            if (LOG_VERBOSE) Log.w(
                TAG, "mTrackReceiver $intent"
            )
        }
    }
    private val mMediaButtonIgnoredReceiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            debugDumpIntent(TAG, "mMediaButtonIgnoredReceiver", intent)
            Toast.makeText(
                this@MainActivity,
                intent.action + " " + dumpBundle(intent.extras),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun processTrackIntent() {
        mCurrentTrack = null
        if (mTrackIntent != null) {
            mCurrentTrack = mTrackIntent!!.getBundleExtra(PowerampAPI.EXTRA_TRACK)
            if (mCurrentTrack != null) {
                val duration = mCurrentTrack!!.getInt(PowerampAPI.Track.DURATION)
                mRemoteTrackTime!!.updateTrackDuration(duration) // Let RemoteTrackTime know about the current song duration.
            }
            val pos = mTrackIntent!!.getIntExtra(
                PowerampAPI.Track.POSITION, -1
            ) // Poweramp build-700+ sends position along with the track intent
            if (pos != -1) {
                mRemoteTrackTime!!.updateTrackPosition(pos)
            }
            updateTrackUI()
            updateAlbumArt(mCurrentTrack)
        }
    }

    private var mStatusReceiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mStatusIntent = intent
            if (LOG_VERBOSE) debugDumpIntent(TAG, "mStatusReceiver", intent)
            updateStatusUI()
        }
    }
    private var mPlayingModeReceiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mPlayingModeIntent = intent
            if (LOG_VERBOSE) debugDumpIntent(TAG, "mPlayingModeReceiver", intent)
            updatePlayingModeUI()
        }
    }

    // This method updates track related info, album art.
    @SuppressLint("SetTextI18n")
    private fun updateTrackUI() {
        Log.w(TAG, "updateTrackUI")
        if (mTrackIntent != null) {
            if (mCurrentTrack != null) {
                (findViewById<View>(R.id.cat) as TextView).text = Integer.toString(
                    mCurrentTrack!!.getInt(PowerampAPI.Track.CAT)
                )
                (findViewById<View>(R.id.uri) as TextView).text =
                    mCurrentTrack!!.getParcelable<Parcelable>(PowerampAPI.Track.CAT_URI).toString()
                (findViewById<View>(R.id.id) as TextView).text = java.lang.Long.toString(
                    mCurrentTrack!!.getLong(PowerampAPI.Track.ID)
                )
                (findViewById<View>(R.id.title) as TextView).text =
                    mCurrentTrack!!.getString(PowerampAPI.Track.TITLE)
                (findViewById<View>(R.id.album) as TextView).text =
                    mCurrentTrack!!.getString(PowerampAPI.Track.ALBUM)
                (findViewById<View>(R.id.artist) as TextView).text =
                    mCurrentTrack!!.getString(PowerampAPI.Track.ARTIST)
                (findViewById<View>(R.id.path) as TextView).text =
                    mCurrentTrack!!.getString(PowerampAPI.Track.PATH)
                val info = StringBuilder()
                info.append("Codec: ").append(mCurrentTrack!!.getString(PowerampAPI.Track.CODEC))
                    .append(" ")
                info.append("Bitrate: ")
                    .append(mCurrentTrack!!.getInt(PowerampAPI.Track.BITRATE, -1)).append(" ")
                info.append("Sample Rate: ")
                    .append(mCurrentTrack!!.getInt(PowerampAPI.Track.SAMPLE_RATE, -1)).append(" ")
                info.append("Channels: ")
                    .append(mCurrentTrack!!.getInt(PowerampAPI.Track.CHANNELS, -1)).append(" ")
                info.append("Duration: ")
                    .append(mCurrentTrack!!.getInt(PowerampAPI.Track.DURATION, -1)).append("sec ")
                (findViewById<View>(R.id.info) as TextView).text = info
                return
            }
        }
        // Else clean everything.
        (findViewById<View>(R.id.info) as TextView).text = ""
        (findViewById<View>(R.id.title) as TextView).text = ""
        (findViewById<View>(R.id.album) as TextView).text = ""
        (findViewById<View>(R.id.artist) as TextView).text = ""
        (findViewById<View>(R.id.path) as TextView).text = ""
    }

    fun updateStatusUI() {
        Log.w(TAG, "updateStatusUI")
        if (mStatusIntent != null) {
            val paused: Boolean
            val state = mStatusIntent!!.getIntExtra(
                PowerampAPI.EXTRA_STATE, PowerampAPI.STATE_NO_STATE
            ) // NOTE: not used here, provides STATE_* int

            // Each status update can contain track position update as well
            val pos = mStatusIntent!!.getIntExtra(PowerampAPI.Track.POSITION, -1)
            if (pos != -1) {
                mRemoteTrackTime!!.updateTrackPosition(pos)
            }
            when (state) {
                PowerampAPI.STATE_PAUSED -> {
                    paused = true
                    startStopRemoteTrackTime(true)
                }
                PowerampAPI.STATE_PLAYING -> {
                    paused = false
                    startStopRemoteTrackTime(false)
                }
                PowerampAPI.STATE_NO_STATE, PowerampAPI.STATE_STOPPED -> {
                    mRemoteTrackTime!!.stopSongProgress()
                    paused = true
                }
                else -> {
                    mRemoteTrackTime!!.stopSongProgress()
                    paused = true
                }
            }
            (findViewById<View>(R.id.play) as Button).text = if (paused) ">" else "||"
        }
    }

    /**
     * Updates shuffle/repeat UI
     */
    fun updatePlayingModeUI() {
        Log.w(TAG, "updatePlayingModeUI")
        if (mPlayingModeIntent != null) {
            val shuffle = mPlayingModeIntent!!.getIntExtra(PowerampAPI.EXTRA_SHUFFLE, -1)
            val shuffleStr: String
            shuffleStr = when (shuffle) {
                PowerampAPI.ShuffleMode.SHUFFLE_ALL -> "Shuffle All"
                PowerampAPI.ShuffleMode.SHUFFLE_CATS -> "Shuffle Categories"
                PowerampAPI.ShuffleMode.SHUFFLE_SONGS -> "Shuffle Songs"
                PowerampAPI.ShuffleMode.SHUFFLE_SONGS_AND_CATS -> "Shuffle Songs And Categories"
                else -> "Shuffle OFF"
            }
            (findViewById<View>(R.id.shuffle) as Button).text = shuffleStr
            val repeat = mPlayingModeIntent!!.getIntExtra(PowerampAPI.EXTRA_REPEAT, -1)
            val repeatStr: String
            repeatStr = when (repeat) {
                PowerampAPI.RepeatMode.REPEAT_ON -> "Repeat List"
                PowerampAPI.RepeatMode.REPEAT_ADVANCE -> "Advance List"
                PowerampAPI.RepeatMode.REPEAT_SONG -> "Repeat Song"
                else -> "Repeat OFF"
            }
            (findViewById<View>(R.id.repeat) as Button).text = repeatStr
        }
    }

    /**
     * Commands RemoteTrackTime to start or stop showing the song progress
     */
    fun startStopRemoteTrackTime(paused: Boolean) {
        if (!paused) {
            mRemoteTrackTime!!.startSongProgress()
        } else {
            mRemoteTrackTime!!.stopSongProgress()
        }
    }

    fun BitMapToString(bitmap: Bitmap): String? {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val b: ByteArray = baos.toByteArray()
        return Base64.encodeToString(b, Base64.DEFAULT)
    }

    fun resizeBitmap(source: Bitmap, maxLength: Int): Bitmap? {
        return try {
            if (source.height >= source.width) {
                if (source.height <= maxLength) { // if image already smaller than the required height
                    return source
                }
                val aspectRatio = source.width.toDouble() / source.height.toDouble()
                val targetWidth = (maxLength * aspectRatio).toInt()
                val result = Bitmap.createScaledBitmap(source, targetWidth, maxLength, false)
                if (result != source) {
                }
                result
            } else {
                if (source.width <= maxLength) { // if image already smaller than the required height
                    return source
                }
                val aspectRatio = source.height.toDouble() / source.width.toDouble()
                val targetHeight = (maxLength * aspectRatio).toInt()
                val result = Bitmap.createScaledBitmap(source, maxLength, targetHeight, false)
                if (result != source) {
                }
                result
            }
        } catch (e: java.lang.Exception) {
            source
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateAlbumArt(track: Bundle?) {
        Log.w(TAG, "updateAlbumArt")
        val aaImage = findViewById<ImageView>(R.id.album_art)
        val albumArtInfo = findViewById<TextView>(R.id.album_art_info)
        if (track == null) {
            Log.w(TAG, "no track")
            aaImage.setImageBitmap(null)
            albumArtInfo.text = "no AA"
            return
        }
        val b = PowerampAPIHelper.getAlbumArt(this, track, 512, 512)
        if (b != null) {
            aaImage.setImageBitmap(b)
            albumArtInfo.text = "scaled w: " + b.width + " h: " + b.height
            var imageInString = resizeBitmap(b,500)?.let { BitMapToString(it) }
            if (imageInString != null) {
                SendThread(datapath, imageInString).start()
            }
        } else {
            albumArtInfo.text = "no AA"
            aaImage.setImageBitmap(null)
        }
    }

    fun play() {
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.TOGGLE_PLAY_PAUSE
            ), FORCE_API_ACTIVITY
        )
    }

    fun prev() {
        Log.w(TAG, "prev")
        // NOTE: since 867. Sending lowcase String command instead of int
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                PowerampAPI.EXTRA_COMMAND, "previous"
            ), FORCE_API_ACTIVITY
        )
    }

    fun next() {
        Log.w(TAG, "next")
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.NEXT
            ), FORCE_API_ACTIVITY
        )
    }

    //button listener
    /*
    override fun onClick(v: View) {
        val message = "Hello wearable"
        //Requires a new thread to avoid blocking the UI
        SendThread(datapath, message).start()
    }
*/
    /**
     * Process a button press. Demonstrates sending various commands to Poweramp
     */
    override fun onClick(v: View) {
        Log.w(TAG, "onClick v=$v")
        val id = v.id
        if (id == R.id.play) {
            Log.w(TAG, "play")
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.TOGGLE_PLAY_PAUSE
                ), FORCE_API_ACTIVITY
            )
        } else if (id == R.id.pause) {
            Log.w(TAG, "pause")
            // NOTE: since 867. Sending String command instead of int
            PowerampAPIHelper.sendPAIntent(
                this,
                Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, "PAUSE"),
                FORCE_API_ACTIVITY
            )
        } else if (id == R.id.prev) {
            Log.w(TAG, "prev")
            // NOTE: since 867. Sending lowcase String command instead of int
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, "previous"
                ), FORCE_API_ACTIVITY
            )
        } else if (id == R.id.next) {
            Log.w(TAG, "next")
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.NEXT
                ), FORCE_API_ACTIVITY
            )
        } else if (id == R.id.prev_in_cat) {
            Log.w(TAG, "prev_in_cat")
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.PREVIOUS_IN_CAT
                ), FORCE_API_ACTIVITY
            )
        } else if (id == R.id.next_in_cat) {
            Log.w(TAG, "next_in_cat")
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.NEXT_IN_CAT
                ), FORCE_API_ACTIVITY
            )
        } else if (id == R.id.repeat) {
            Log.w(TAG, "repeat")
            // No toast for this button just for demo.
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.REPEAT
                ), FORCE_API_ACTIVITY
            )
        } else if (id == R.id.shuffle) {
            Log.w(TAG, "shuffle")
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SHUFFLE
                ), FORCE_API_ACTIVITY
            )
        } else if (id == R.id.repeat_all) {
            Log.w(TAG, "repeat_all")
            PowerampAPIHelper.sendPAIntent(
                this,
                Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.REPEAT
                ).putExtra(PowerampAPI.EXTRA_REPEAT, PowerampAPI.RepeatMode.REPEAT_ON),
                FORCE_API_ACTIVITY
            )
        } else if (id == R.id.repeat_off) {
            Log.w(TAG, "repeat_off")
            PowerampAPIHelper.sendPAIntent(
                this,
                Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.REPEAT
                ).putExtra(PowerampAPI.EXTRA_REPEAT, PowerampAPI.RepeatMode.REPEAT_NONE),
                FORCE_API_ACTIVITY
            )
        } else if (id == R.id.shuffle_all) {
            Log.w(TAG, "shuffle_all")
            PowerampAPIHelper.sendPAIntent(
                this,
                Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SHUFFLE
                ).putExtra(PowerampAPI.EXTRA_SHUFFLE, PowerampAPI.ShuffleMode.SHUFFLE_ALL),
                FORCE_API_ACTIVITY
            )
        } else if (id == R.id.shuffle_off) {
            Log.w(TAG, "shuffle_all")
            PowerampAPIHelper.sendPAIntent(
                this,
                Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SHUFFLE
                ).putExtra(PowerampAPI.EXTRA_SHUFFLE, PowerampAPI.ShuffleMode.SHUFFLE_NONE),
                FORCE_API_ACTIVITY
            )
        } else if (id == R.id.commit_eq) {
            Log.w(TAG, "commit_eq")
            commitEq()
        } else if (id == R.id.play_file) {
            Log.w(TAG, "play_file")
            try {
                val uri = (findViewById<View>(R.id.play_file_path) as TextView).text.toString()
                if (uri.length > "content://".length) {
                    PowerampAPIHelper.sendPAIntent(
                        this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                            PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY
                        ) //.putExtra(PowerampAPI.Track.POSITION, 10) // Play from 10th second.
                            .setData(Uri.parse(uri)), FORCE_API_ACTIVITY
                    )
                }
            } catch (th: Throwable) {
                Log.e(TAG, "", th)
                Toast.makeText(this, th.message, Toast.LENGTH_LONG).show()
            }
        } else if (id == R.id.play_album) {
            playAlbum()
        } else if (id == R.id.play_all_songs) {
            playAllSongs()
        } else if (id == R.id.play_second_artist_first_album) {
            playSecondArtistFirstAlbum()
        } else if (id == R.id.pa_current_list) {
            startActivity(Intent(PowerampAPI.ACTION_SHOW_CURRENT))
        } else if (id == R.id.pa_folders) {
            startActivity(
                Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(
                    PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("folders").build()
                )
            )
        } else if (id == R.id.pa_all_songs) {
            startActivity(
                Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(
                    PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("files").build()
                )
            )
        } else if (id == R.id.create_playlist) {
            createPlaylistAndAddToIt()
        } else if (id == R.id.create_playlist_w_streams) {
            createPlaylistWStreams()
        } else if (id == R.id.goto_created_playlist) {
            gotoCreatedPlaylist()
        } else if (id == R.id.add_to_q_and_goto_q) {
            addToQAndGotoQ()
        } else if (id == R.id.queue) {
            startActivity(
                Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(
                    PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("queue").build()
                )
            )
        } else if (id == R.id.get_all_prefs) {
            allPrefs
        } else if (id == R.id.get_pref) {
            pref
        }
    }

    fun seekBackward10s(view: View?) {
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SEEK
            ).putExtra(PowerampAPI.EXTRA_RELATIVE_POSITION, -10).putExtra(
                PowerampAPI.EXTRA_LOCK, true
            ) // If EXTRA_LOCK=true, we don't change track by seeking past start/end
            , FORCE_API_ACTIVITY
        )
    }

    fun seekForward10s(view: View?) {
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SEEK
            ).putExtra(PowerampAPI.EXTRA_RELATIVE_POSITION, 10).putExtra(
                PowerampAPI.EXTRA_LOCK, true
            ) // If EXTRA_LOCK=true, we don't change track by seeking past start/end
            , FORCE_API_ACTIVITY
        )
    }

    fun exportPrefs(view: View?) {
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.Settings.ACTION_EXPORT_SETTINGS).putExtra(
                PowerampAPI.Settings.EXTRA_UI, true
            ), FORCE_API_ACTIVITY
        )
    }

    fun importPrefs(view: View?) {
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.Settings.ACTION_IMPORT_SETTINGS).putExtra(
                PowerampAPI.Settings.EXTRA_UI, true
            ), FORCE_API_ACTIVITY
        )
    }

    /**
     * Get the specified preference and show its name, type, value
     */
    @get:SuppressLint("SetTextI18n")
    private val pref: Unit
        private get() {
            val prefET = findViewById<EditText>(R.id.pref)
            val prefName = prefET.text.toString()
            val prefsTV = findViewById<TextView>(R.id.prefs)
            if (prefName.length > 0) {
                val bundle = Bundle()
                bundle.putString(prefName, null)
                val resultPrefs = contentResolver.call(
                    PowerampAPI.ROOT_URI, PowerampAPI.CALL_PREFERENCE, null, bundle
                )
                if (resultPrefs != null) {
                    val value = resultPrefs[prefName]
                    if (value != null) {
                        prefsTV.text = prefName + " (" + value.javaClass.simpleName + "): " + value
                        prefsTV.background = null
                    } else {
                        prefsTV.text = "$prefName: <no value>"
                        prefsTV.setBackgroundColor(0x55FF0000)
                    }
                    prefsTV.parent.requestChildFocus(prefsTV, prefsTV)
                } else {
                    prefsTV.text = "Call failed"
                    prefsTV.setBackgroundColor(0x55FF0000)
                }
            }
        }

    /**
     * Get all available preferences and dump the resulting bundle
     */
    private val allPrefs: Unit
        private get() {
            val prefsTV = findViewById<TextView>(R.id.prefs)
            val resultPrefs =
                contentResolver.call(PowerampAPI.ROOT_URI, PowerampAPI.CALL_PREFERENCE, null, null)
            prefsTV.text = dumpBundle(resultPrefs)
            prefsTV.parent.requestChildFocus(prefsTV, prefsTV)
        }

    fun setPref(view: View?) {
        val pref = findViewById<EditText>(R.id.pref)
        val name = pref.text.toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(name)) {
            pref.error = "Empty"
            return
        }
        pref.error = null
        val prefValue = findViewById<EditText>(R.id.pref_value)
        val value = prefValue.text.toString().trim { it <= ' ' }
        val request = Bundle()
        prefValue.error = null
        var failed = false
        // Guess the type from the value
        if (TextUtils.isEmpty(value)) {
            request.putString(name, value) // Empty value is possible only for the String
        } else if ("true" == value) {
            request.putBoolean(name, true)
        } else if ("false" == value) {
            request.putBoolean(name, false)
        } else {
            try {
                val intValue = value.toInt()
                // We are able to parse this as int, though preference can be any type.
                // Real code should decide the type based on existing knowledge of the preference type, which don't have here
                request.putInt(name, intValue)
            } catch (ex: NumberFormatException) {
                try {
                    val intValue = value.toFloat()
                    // We are able to parse this as float, though actual preference can by any type.
                    // Real code should decide the type based on existing knowledge of the preference type, which don't have here
                    request.putFloat(name, intValue)
                } catch (ex2: NumberFormatException) {
                    prefValue.error = "Failed to guess type"
                    failed = true
                }
            }
        }
        if (!failed) {
            val prefsTV = findViewById<TextView>(R.id.prefs)

            // OK, let's call it
            val resultPrefs = contentResolver.call(
                PowerampAPI.ROOT_URI, PowerampAPI.CALL_SET_PREFERENCE, null, request
            )
            prefsTV.text = dumpBundle(resultPrefs)
            prefsTV.parent.requestChildFocus(prefsTV, prefsTV)
        }
    }

    /**
     * Process some long presses
     */
    override fun onLongClick(v: View): Boolean {
        val id = v.id
        if (id == R.id.play) {
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.STOP
                ), FORCE_API_ACTIVITY
            )
            return true
        } else if (id == R.id.next) {
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.BEGIN_FAST_FORWARD
                ), FORCE_API_ACTIVITY
            )
            mProcessingLongPress = true
            return true
        } else if (id == R.id.prev) {
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.BEGIN_REWIND
                ), FORCE_API_ACTIVITY
            )
            mProcessingLongPress = true
            return true
        }
        return false
    }

    /**
     * Process touch up event to stop ff/rw
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val id = v.id
                if (id == R.id.next) {
                    Log.e(TAG, "onTouch next ACTION_UP")
                    if (mProcessingLongPress) {
                        PowerampAPIHelper.sendPAIntent(
                            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.END_FAST_FORWARD
                            ), FORCE_API_ACTIVITY
                        )
                        mProcessingLongPress = false
                    }
                    return false
                } else if (id == R.id.prev) {
                    if (mProcessingLongPress) {
                        PowerampAPIHelper.sendPAIntent(
                            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.END_REWIND
                            ), FORCE_API_ACTIVITY
                        )
                        mProcessingLongPress = false
                    }
                    return false
                }
            }
        }
        return false
    }

    /**
     * Just play all library songs (starting from the first)
     */
    private fun playAllSongs() {
        PowerampAPIHelper.sendPAIntent(
            this,
            Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY
            ).setData(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("files").build()),
            FORCE_API_ACTIVITY
        )
    }

    /**
     * Get first album id and play it
     */
    private fun playAlbum() {
        val c = contentResolver.query(
            PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("albums").build(),
            arrayOf("albums._id", "album"),
            null,
            null,
            "album"
        )
        if (c != null) {
            if (c.moveToNext()) {
                val albumId = c.getLong(0)
                val name = c.getString(1)
                Toast.makeText(this, "Playing album: $name", Toast.LENGTH_SHORT).show()
                PowerampAPIHelper.sendPAIntent(
                    this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                        PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY
                    ).setData(
                        PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("albums")
                            .appendEncodedPath(java.lang.Long.toString(albumId))
                            .appendEncodedPath("files").build()
                    ), FORCE_API_ACTIVITY
                )
            }
            c.close()
        }
    }

    /**
     * Play first available album from the first available artist in ARTIST_ALBUMs
     */
    private fun playSecondArtistFirstAlbum() {
        // Get first artist.
        val c = contentResolver.query(
            PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("artists").build(),
            arrayOf("artists._id", "artist"),
            null,
            null,
            "artist_sort COLLATE NOCASE"
        )
        if (c != null) {
            c.moveToNext() // First artist.
            if (c.moveToNext()) { // Second artist.
                val artistId = c.getLong(0)
                val artist = c.getString(1)
                val c2 = contentResolver.query(
                    PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("artists_albums").build(),
                    arrayOf("albums._id", "album"),
                    "artists._id=?",
                    arrayOf(java.lang.Long.toString(artistId)),
                    "album_sort COLLATE NOCASE"
                )
                if (c2 != null) {
                    if (c2.moveToNext()) {
                        val albumId = c2.getLong(0)
                        val album = c2.getString(1)
                        Toast.makeText(
                            this, "Playing artist: $artist album: $album", Toast.LENGTH_SHORT
                        ).show()
                        PowerampAPIHelper.sendPAIntent(
                            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY
                            ).setData(
                                PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("artists")
                                    .appendEncodedPath(java.lang.Long.toString(artistId))
                                    .appendEncodedPath("albums")
                                    .appendEncodedPath(java.lang.Long.toString(albumId))
                                    .appendEncodedPath("files").build()
                            ), FORCE_API_ACTIVITY
                        )
                    }
                    c2.close()
                }
            }
            c.close()
        }
    }

    /**
     * Event handler for Dynamic Eq checkbox
     */
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        findViewById<View>(R.id.commit_eq).isEnabled = !isChecked
    }

    /**
     * Generates and sends presetString to Poweramp Eq
     */
    private fun commitEq() {
        val presetString = StringBuilder()
        val equLayout = findViewById<TableLayout>(R.id.equ_layout)
        val count = equLayout.childCount
        for (i in count - 1 downTo 0) {
            val bar = (equLayout.getChildAt(i) as ViewGroup).getChildAt(1) as SeekBar
            val name = bar.tag as String
            val value = seekBarToValue(name, bar.progress)
            presetString.append(name).append("=").append(value).append(";")
        }
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_EQU_STRING
            ).putExtra(PowerampAPI.EXTRA_VALUE, presetString.toString()), FORCE_API_ACTIVITY
        )
    }

    /**
     * Applies correct seekBar-to-float scaling
     */
    private fun seekBarToValue(name: String, progress: Int): Float {
        val value: Float
        value = if ("preamp" == name || "bass" == name || "treble" == name) {
            progress / 100f
        } else {
            (progress - 100) / 100f
        }
        return value
    }

    /**
     * Event handler for both song progress seekbar and equalizer bands
     */
    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
        val id = bar.id
        if (id == R.id.song_seekbar) {
            if (fromUser) {
                sendSeek(false)
            }
        } else if (id == R.id.sleep_timer_seekbar) {
            updateSleepTimer(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}

    /**
     * Force seek when user ends seeking
     */
    override fun onStopTrackingTouch(seekBar: SeekBar) {
        sendSeek(true)
    }

    /**
     * Send a seek command
     */
    private fun sendSeek(ignoreThrottling: Boolean) {
        val position = mSongSeekBar!!.progress
        mRemoteTrackTime!!.updateTrackPosition(position)

        // Apply some throttling to avoid too many intents to be generated.
        if (mLastSeekSentTime == 0L || System.currentTimeMillis() - mLastSeekSentTime > SEEK_THROTTLE || ignoreThrottling && mLastSentSeekPosition != position // Do not send same position for cases like quick seekbar touch
        ) {
            mLastSeekSentTime = System.currentTimeMillis()
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SEEK
                ).putExtra(PowerampAPI.Track.POSITION, position), FORCE_API_ACTIVITY
            )
            mLastSentSeekPosition = position
            Log.w(
                TAG, "sendSeek sent position=$position"
            )
        } else {
            Log.w(TAG, "sendSeek throttled")
        }
    }

    /**
     * Event handler for Presets spinner
     */
    override fun onItemSelected(adapter: AdapterView<*>?, item: View, pos: Int, id: Long) {
        if (!mSettingPreset) {
            PowerampAPIHelper.sendPAIntent(
                this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                    PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_EQU_PRESET
                ).putExtra(PowerampAPI.EXTRA_ID, id), FORCE_API_ACTIVITY
            )
        } else {
            mSettingPreset = false
        }
    }

    override fun onNothingSelected(arg0: AdapterView<*>?) {}

    /**
     * Callback from RemoteTrackTime. Updates durations (both seekbar max value and duration label)
     */
    override fun onTrackDurationChanged(duration: Int) {
        mDurationBuffer.setLength(0)
        formatTimeS(mDurationBuffer, duration, true)
        mDuration!!.text = mDurationBuffer
        mSongSeekBar!!.max = duration
    }

    /**
     * Callback from RemoteTrackTime. Updates the current song progress. Ensures extra event is not processed (mUpdatingSongSeekBar).
     */
    override fun onTrackPositionChanged(position: Int) {
        mElapsedBuffer.setLength(0)
        formatTimeS(mElapsedBuffer, position, false)
        mElapsed!!.text = mElapsedBuffer
        if (mSongSeekBar!!.isPressed) {
            return
        }
        mSongSeekBar!!.progress = position
    }

    fun setSleepTimer(view: View?) {
        PowerampAPIHelper.sendPAIntent(
            this, Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
                PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SLEEP_TIMER
            ).putExtra(
                PowerampAPI.EXTRA_SECONDS,
                (findViewById<View>(R.id.sleep_timer_seekbar) as SeekBar).progress
            ).putExtra(
                PowerampAPI.EXTRA_PLAY_TO_END,
                (findViewById<View>(R.id.sleep_timer_play_to_end) as CheckBox).isChecked
            ), FORCE_API_ACTIVITY
        )
    }

    fun rescan(view: View?) {
        val intent = Intent(PowerampAPI.Scanner.ACTION_SCAN_DIRS).setComponent(
            PowerampAPIHelper.getScannerServiceComponentName(this)
        ).putExtra(
            PowerampAPI.Scanner.EXTRA_CAUSE, "$packageName user requested"
        )
        startService(intent)
    }

    fun milkRescan(view: View?) {
        val intent = Intent(PowerampAPI.MilkScanner.ACTION_SCAN).putExtra(
            PowerampAPI.MilkScanner.EXTRA_CAUSE, "$packageName user requested"
        )
        if (PowerampAPIHelper.getPowerampBuild(this) >= 868) {
            PowerampAPIHelper.sendPAIntent(this, intent, FORCE_API_ACTIVITY) // Since 868
        } else {
            intent.component =
                PowerampAPIHelper.getMilkScannerServiceComponentName(this) // Used prior build 868
            startService(intent)
        }
    }

    // =================================================
    @SuppressLint("SetTextI18n")
    private fun updateSleepTimer(progress: Int) {
        (findViewById<View>(R.id.sleep_timer_value) as TextView).text = "Seep in " + progress + "s"
    }// code==0 here

    /**
     * Retrieves Poweramp build number and normalizes it to ### form, e.g. 846002 => 846
     */
    private val powerampBuildNumber: Int
        private get() {
            var code = mPowerampBuildNumber
            if (code == 0) {
                try {
                    code = packageManager.getPackageInfo(
                        PowerampAPIHelper.getPowerampPackageName(this), 0
                    ).versionCode
                } catch (ex: PackageManager.NameNotFoundException) {
                    // code==0 here
                    Log.e(TAG, "", ex)
                }
                if (code > 1000) {
                    code = code / 1000
                }
                mPowerampBuildNumber = code
            }
            return code
        }

    /**
     * NOTE: real code should run on some worker thread
     */
    private fun createPlaylistAndAddToIt() {
        val buildNumber = powerampBuildNumber
        val cr = contentResolver
        val playlistsUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("playlists").build()

        // NOTE: we need raw column names for an insert query (without table name), thus using getRawColName()
        val values = ContentValues()
        values.put(
            getRawColName(TableDefs.Playlists.PLAYLIST),
            "Sample Playlist " + System.currentTimeMillis()
        )
        val playlistInsertedUri = cr.insert(playlistsUri, values)
        if (playlistInsertedUri != null) {
            Log.w(
                TAG, "createPlaylistAndAddToIt inserted=$playlistInsertedUri"
            )

            // NOTE: we are inserting into /playlists/#/files, playlistInsertedUri (/playlists/#) is not valid for entries insertion
            val playlistEntriesUri =
                playlistInsertedUri.buildUpon().appendEncodedPath("files").build()
            mLastCreatedPlaylistFilesUri = playlistEntriesUri

            // Select up to 10 random files
            val numFilesToInsert = 10
            val filesUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("files").build()
            val c = contentResolver.query(
                filesUri,
                arrayOf(TableDefs.Files._ID, TableDefs.Files.NAME, TableDefs.Folders.PATH),
                null,
                null,
                "RANDOM() LIMIT $numFilesToInsert"
            )
            var sort = 0
            if (c != null) {
                while (c.moveToNext()) {
                    val fileId = c.getLong(0)
                    val fileName = c.getString(1)
                    val folderPath = c.getString(2)
                    values.clear()
                    values.put(getRawColName(TableDefs.PlaylistEntries.FOLDER_FILE_ID), fileId)

                    // Playlist behavior changed in Poweramp build 842 - now each playlist entry should contain full path
                    // This restriction was uplifted in build 846, but anyway, it's preferable to fill playlist entry folder_path and file_name columns to allow
                    // easy resolution of playlist entries in case user changes music folders, storage, etc.
                    if (buildNumber >= 842) {
                        values.put(getRawColName(TableDefs.PlaylistEntries.FOLDER_PATH), folderPath)
                        values.put(getRawColName(TableDefs.PlaylistEntries.FILE_NAME), fileName)
                    }

                    // Playlist entries are always sorted by "sort" fields, so if we want them to be in order, we should provide it.
                    // If we're adding entries to existing playlist, it's a good idea to get MAX(sort) first from the given playlist
                    values.put(getRawColName(TableDefs.PlaylistEntries.SORT), sort)
                    val entryUri = cr.insert(playlistEntriesUri, values)
                    if (entryUri != null) {
                        Log.w(
                            TAG,
                            "createPlaylistAndAddToIt inserted entry fileId=$fileId sort=$sort folderPath=$folderPath fileName=$fileName entryUri=$entryUri"
                        )
                        sort++
                    } else {
                        Log.e(
                            TAG, "createPlaylistAndAddToIt FAILED to insert entry fileId=$fileId"
                        )
                    }
                }
                c.close()
                Toast.makeText(this, "Inserted files=$sort", Toast.LENGTH_SHORT).show()
            }
            if (sort > 0) {
                // Force Poweramp to reload data in UI / PlayerService as we changed something
                val intent = Intent(PowerampAPI.ACTION_RELOAD_DATA)
                intent.setPackage(PowerampAPIHelper.getPowerampPackageName(this))
                intent.putExtra(PowerampAPI.EXTRA_PACKAGE, packageName)
                // NOTE: important to send the changed table for an adequate UI / PlayerService reloading
                intent.putExtra(PowerampAPI.EXTRA_TABLE, TableDefs.PlaylistEntries.TABLE)
                if (FORCE_API_ACTIVITY) {
                    intent.component = PowerampAPIHelper.getApiActivityComponentName(this)
                    startActivity(intent)
                } else {
                    sendBroadcast(intent)
                }
            }

            // Make open playlist button active
            findViewById<View>(R.id.goto_created_playlist).isEnabled = true
        } else {
            Log.e(TAG, "createPlaylistAndAddToIt FAILED")
        }
    }

    /**
     * Demonstrates a playlist with the http stream entries<br></br>
     * NOTE: real code should run on some worker thread
     */
    private fun createPlaylistWStreams() {
        val buildNumber = powerampBuildNumber
        // We need at least 842 build
        if (buildNumber < 842) {
            Toast.makeText(this, "Poweramp build is too old", Toast.LENGTH_SHORT).show()
            return
        }
        val cr = contentResolver
        val playlistsUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("playlists").build()

        // NOTE: we need raw column names for an insert query (without table name), thus using getRawColName()
        // NOTE: playlist with a stream doesn't differ from other (track based) playlists. Only playlist entries differ vs usual file tracks
        val playlistName = "Stream Playlist " + System.currentTimeMillis()
        val values = ContentValues()
        values.put(getRawColName(TableDefs.Playlists.PLAYLIST), playlistName)
        val playlistInsertedUri = cr.insert(playlistsUri, values)
        if (playlistInsertedUri == null) {
            Toast.makeText(this, "Failed to create playlist", Toast.LENGTH_SHORT).show()
            return
        }
        Log.w(
            TAG, "createPlaylistAndAddToIt inserted=$playlistInsertedUri"
        )

        // NOTE: we are inserting into /playlists/#/files, playlistInsertedUri (/playlists/#) is not valid for the entries insertion
        val playlistEntriesUri = playlistInsertedUri.buildUpon().appendEncodedPath("files").build()
        mLastCreatedPlaylistFilesUri = playlistEntriesUri

        // To create stream entry, we just provide the url. Entry is added as the last one
        values.clear()
        values.put(
            getRawColName(TableDefs.PlaylistEntries.FILE_NAME), "http://64.71.77.150:8000/stream"
        )
        val entryUri1 = cr.insert(playlistEntriesUri, values)
        values.clear()
        values.put(
            getRawColName(TableDefs.PlaylistEntries.FILE_NAME), "http://94.23.205.82:5726/;stream/1"
        )
        val entryUri2 = cr.insert(playlistEntriesUri, values)
        if (entryUri1 != null && entryUri2 != null) {
            Toast.makeText(this, "Inserted streams OK, playlist=$playlistName", Toast.LENGTH_SHORT)
                .show()

            // Force Poweramp to reload data in UI / PlayerService as we changed something
            val intent = Intent(PowerampAPI.ACTION_RELOAD_DATA)
            intent.setPackage(PowerampAPIHelper.getPowerampPackageName(this))
            intent.putExtra(PowerampAPI.EXTRA_PACKAGE, packageName)
            // NOTE: important to send the changed table for an adequate UI / PlayerService reloading
            intent.putExtra(PowerampAPI.EXTRA_TABLE, TableDefs.PlaylistEntries.TABLE)
            if (FORCE_API_ACTIVITY) {
                intent.component = PowerampAPIHelper.getApiActivityComponentName(this)
                startActivity(intent)
            } else {
                sendBroadcast(intent)
            }
        }

        // Make open playlist button active
        findViewById<View>(R.id.goto_created_playlist).isEnabled = true
    }

    private fun gotoCreatedPlaylist() {
        if (mLastCreatedPlaylistFilesUri != null) {
            startActivity(
                Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(
                    mLastCreatedPlaylistFilesUri
                )
            )
        }
    }

    private fun addToQAndGotoQ() {
        val cr = contentResolver
        val queueUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("queue").build()
        val values = ContentValues()

        // Get max sort from queue
        var maxSort = 0
        var c = contentResolver.query(
            queueUri, arrayOf("MAX(" + TableDefs.Queue.SORT + ")"), null, null, null
        )
        if (c != null) {
            if (c.moveToFirst()) {
                maxSort = c.getInt(0)
            }
            c.close()
        }

        // Select up to 10 random files
        val numFilesToInsert = 10
        val filesUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("files").build()
        c = contentResolver.query(
            filesUri,
            arrayOf(TableDefs.Files._ID, TableDefs.Files.NAME),
            null,
            null,
            "RANDOM() LIMIT $numFilesToInsert"
        )
        var inserted = 0
        if (c != null) {
            var sort = maxSort + 1 // Start from maxSort + 1
            while (c.moveToNext()) {
                val fileId = c.getLong(0)
                val name = c.getString(1)
                values.clear()
                values.put(getRawColName(TableDefs.Queue.FOLDER_FILE_ID), fileId)
                values.put(getRawColName(TableDefs.Queue.SORT), sort)
                val entryUri = cr.insert(queueUri, values)
                if (entryUri != null) {
                    Log.w(
                        TAG,
                        "addToQAndGotoQ inserted entry fileId=$fileId sort=$sort name=$name entryUri=$entryUri"
                    )
                    sort++
                    inserted++
                } else {
                    Log.e(
                        TAG, "addToQAndGotoQ FAILED to insert entry fileId=$fileId"
                    )
                }
            }
            c.close()
            Toast.makeText(this, "Inserted files=$sort", Toast.LENGTH_SHORT).show()
        }
        if (inserted > 0) {
            // Force Poweramp to reload data in UI / PlayerService as we changed something
            val intent = Intent(PowerampAPI.ACTION_RELOAD_DATA)
            intent.setPackage(PowerampAPIHelper.getPowerampPackageName(this))
            intent.putExtra(PowerampAPI.EXTRA_PACKAGE, packageName)
            // NOTE: important to send changed table for the adequate UI / PlayerService reloading. This can also make Poweramp to go to Queue
            intent.putExtra(PowerampAPI.EXTRA_TABLE, TableDefs.Queue.TABLE)
            if (FORCE_API_ACTIVITY) {
                intent.component = PowerampAPIHelper.getApiActivityComponentName(this)
                startActivity(intent)
            } else {
                sendBroadcast(intent)
            }
            startActivity(Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(queueUri))
        }
    }

    private val componentNames: Unit
        private get() {
            if (LOG_VERBOSE) Log.w(TAG, "getComponentNames")
            val tv = findViewById<TextView>(R.id.component_names)
            val sb = SpannableStringBuilder()
            appendWithSpan(sb, "Component Names\n", StyleSpan(Typeface.BOLD))
            appendWithSpan(
                sb, "Package: ", StyleSpan(Typeface.BOLD)
            ).append(PowerampAPIHelper.getPowerampPackageName(this)).append("\n")
            appendWithSpan(
                sb, "PlayerService: ", StyleSpan(Typeface.BOLD)
            ).append(PowerampAPIHelper.getPlayerServiceComponentName(this).toString()).append("\n")
            appendWithSpan(sb, "MediaBrowserService: ", StyleSpan(Typeface.BOLD)).append(
                PowerampAPIHelper.getBrowserServiceComponentName(this).toString()
            ).append("\n")
            appendWithSpan(
                sb, "API Receiver: ", StyleSpan(Typeface.BOLD)
            ).append(PowerampAPIHelper.getApiReceiverComponentName(this).toString()).append("\n")
            appendWithSpan(
                sb, "Scanner: ", StyleSpan(Typeface.BOLD)
            ).append(PowerampAPIHelper.getScannerServiceComponentName(this).toString()).append("\n")
            appendWithSpan(
                sb, "Milk Scanner: ", StyleSpan(Typeface.BOLD)
            ).append(PowerampAPIHelper.getMilkScannerServiceComponentName(this).toString())
                .append("\n")
            tv.text = sb
            if (LOG_VERBOSE) Log.w(TAG, "getComponentNames DONE")
        }

    companion object {
        private const val TAG = "MainActivity"
        private const val LOG_VERBOSE = false

        /**
         * If set to true, we send all our intents to API activity. Use for Poweramp build 862+
         */
        const val FORCE_API_ACTIVITY = true
        private val NO_TIME = charArrayOf('-', ':', '-', '-')
        private const val SEEK_THROTTLE = 500
        private var sPermissionAsked = false

        @NonNull
        fun getRawColName(@NonNull col: String): String {
            val dot = col.indexOf('.')
            return if (dot >= 0 && dot + 1 <= col.length) {
                col.substring(dot + 1)
            } else col
        }

        fun formatTimeS(@NonNull sb: StringBuilder, secs: Int, showPlaceholderForZero: Boolean) {
            if (secs < 0 || secs == 0 && showPlaceholderForZero) {
                sb.append(NO_TIME)
                return
            }
            val seconds = secs % 60
            if (secs < 3600) { // min:sec
                val minutes = secs / 60
                sb.append(minutes).append(':')
            } else { // hour:min:sec
                val hours = secs / 3600
                val minutes = secs / 60 % 60
                sb.append(hours).append(':')
                if (minutes < 10) {
                    sb.append('0')
                }
                sb.append(minutes).append(':')
            }
            if (seconds < 10) {
                sb.append('0')
            }
            sb.append(seconds)
        }

        fun debugDumpIntent(
            tag: String?, @NonNull description: String, @Nullable intent: Intent?
        ) {
            if (intent != null) {
                Log.w(
                    tag,
                    description + " debugDumpIntent action=" + intent.action + " extras=" + dumpBundle(
                        intent.extras
                    )
                )
                val track = intent.getBundleExtra(PowerampAPI.EXTRA_TRACK)
                if (track != null) {
                    Log.w(tag, "track=" + dumpBundle(track))
                }
            } else {
                Log.e(tag, "$description debugDumpIntent intent is null")
            }
        }

        @NonNull
        fun dumpBundle(@Nullable bundle: Bundle?): String {
            if (bundle == null) {
                return "null bundle"
            }
            val sb = StringBuilder()
            val keys = bundle.keySet()
            sb.append("\n")
            for (key in keys) {
                sb.append('\t').append(key).append("=")
                val `val` = bundle[key]
                sb.append(`val`)
                if (`val` != null) {
                    sb.append(" ").append(`val`.javaClass.simpleName)
                }
                sb.append("\n")
            }
            return sb.toString()
        }

        @NonNull
        private fun appendWithSpan(
            @NonNull sb: SpannableStringBuilder, @Nullable str: CharSequence?, @NonNull span: Any
        ): SpannableStringBuilder {
            val start = sb.length
            sb.append(str ?: "")
            sb.setSpan(span, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }
    }
}