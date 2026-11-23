package app.roundly.wear.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import app.roundly.wear.alert.WearAlertController
import app.roundly.wear.data.WatchRepository
import app.roundly.wear.di.WearAppScope
import app.roundly.wear.sync.PhoneLink
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Dependencies for watch receivers and services, resolved from the application component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearEntryPoint {
    fun scheduler(): WatchScheduler
    fun alertController(): WearAlertController
    fun repository(): WatchRepository
    fun phoneLink(): PhoneLink

    @WearAppScope
    fun scope(): CoroutineScope
}

fun Context.wearDeps(): WearEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WearEntryPoint::class.java)

internal fun BroadcastReceiver.runAsync(context: Context, block: suspend WearEntryPoint.() -> Unit) {
    val deps = context.wearDeps()
    val pending = goAsync()
    deps.scope().launch {
        try {
            deps.block()
        } finally {
            pending.finish()
        }
    }
}
