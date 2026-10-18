package com.millerchnz.ghzexplorer.bluetooth

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.millerchnz.ghzexplorer.AppForegroundTracker
import com.millerchnz.ghzexplorer.MainActivity
import com.millerchnz.ghzexplorer.R
import com.millerchnz.ghzexplorer.data.RadioText
import com.millerchnz.ghzexplorer.data.SettingsStore
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records pairing requests, bond changes, and ACL connections aimed at this phone.
 * The receiver lives for the process. Nothing here accepts or rejects the pairing;
 * the system dialog still does that.
 */
class PairingLog(private val app: Application) {
    data class Event(
        val atMillis: Long,
        val title: String,
        val address: String,
        val deviceName: String,
        val detail: String,
    ) {
        val line: String
            get() = "${RadioText.formatTime(atMillis)} · $deviceName · $address"
    }

    private val prefs = app.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.bluetoothDevice() ?: return
            val name = BluetoothFields.safeName(device) ?: intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: "Unnamed device"
            val address = device.address ?: "unknown"
            val event = when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                    val key = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, Int.MIN_VALUE)
                    val keyText = if (key == Int.MIN_VALUE) "no key in the broadcast" else "key $key"
                    Event(
                        atMillis = System.currentTimeMillis(),
                        title = "Pairing request",
                        address = address,
                        deviceName = name,
                        detail = "${BluetoothFields.pairingVariant(variant)} · $keyText",
                    )
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val next = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                    Event(
                        atMillis = System.currentTimeMillis(),
                        title = "Bond ${BluetoothFields.bondName(previous)} → ${BluetoothFields.bondName(next)}",
                        address = address,
                        deviceName = name,
                        detail = "The phone's stored bond changed.",
                    )
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> Event(
                    atMillis = System.currentTimeMillis(),
                    title = "Connected",
                    address = address,
                    deviceName = name,
                    detail = "An ACL link to this phone came up.",
                )
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> Event(
                    atMillis = System.currentTimeMillis(),
                    title = "Disconnected",
                    address = address,
                    deviceName = name,
                    detail = "An ACL link to this phone dropped.",
                )
                else -> return
            }
            remember(event)
            if (intent.action == BluetoothDevice.ACTION_PAIRING_REQUEST && !AppForegroundTracker.isForeground) {
                notify(event)
            }
        }
    }

    fun start() {
        if (_events.value.isEmpty()) {
            _events.value = load()
        }
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
        ensureChannel()
    }

    private fun remember(event: Event) {
        val next = listOf(event) + _events.value
        _events.value = next.take(MAX_EVENTS)
        prefs.edit().putString(KEY, _events.value.joinToString("\n") { encode(it) }).apply()
    }

    private fun load(): List<Event> =
        prefs.getString(KEY, null).orEmpty().lineSequence().mapNotNull { decode(it) }.take(MAX_EVENTS).toList()

    private fun notify(event: Event) {
        if (AppPermissions.missing(app, AppPermissions.notifications()).isNotEmpty()) return
        val open = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${event.deviceName} is trying to pair")
            .setContentText("${event.detail} · ${event.address}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "Pairing requests", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "A device asked this phone to pair while 2.4 GHz Explorer was in the background."
        manager.createNotificationChannel(channel)
    }

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        private const val KEY = "pairing_events"
        private const val CHANNEL = "pairing"
        private const val NOTIFICATION_ID = 24
        private const val MAX_EVENTS = 40

        private fun encode(event: Event): String =
            listOf(
                event.atMillis.toString(),
                event.title,
                event.address,
                event.deviceName,
                event.detail,
            ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

        private fun decode(line: String): Event? {
            val parts = line.split('\t')
            if (parts.size < 5) return null
            return Event(
                atMillis = parts[0].toLongOrNull() ?: return null,
                title = parts[1],
                address = parts[2],
                deviceName = parts[3],
                detail = parts[4],
            )
        }
    }
}
