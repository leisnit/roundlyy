package app.roundly.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import app.roundly.alert.AlertController
import app.roundly.data.Housekeeping
import app.roundly.di.ApplicationScope
import app.roundly.watch.WatchBridge
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Dependencies for manifest receivers, resolved from the application component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun scheduler(): AlarmScheduler
    fun alertController(): AlertController
    fun housekeeping(): Housekeeping
    fun watchBridge(): WatchBridge

    @ApplicationScope
    fun scope(): CoroutineScope
}

internal fun Context.receiverDeps(): ReceiverEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReceiverEntryPoint::class.java)

/** Runs [block] off the main thread while keeping the broadcast (and its wake lock) alive. */
internal fun BroadcastReceiver.runAsync(context: Context, block: suspend ReceiverEntryPoint.() -> Unit) {
    val deps = context.receiverDeps()
    val pending = goAsync()
    deps.scope().launch {
        try {
            deps.block()
        } finally {
            pending.finish()
        }
    }
}
