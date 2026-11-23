package app.roundly.di

import app.roundly.core.schedule.ScheduleCalculator
import app.roundly.core.schedule.SecureRandomSource
import app.roundly.core.time.Clock
import app.roundly.watch.DataLayerWatchBridge
import app.roundly.watch.WatchBridge
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SchedulingModule {

    @Provides
    @Singleton
    fun provideScheduleCalculator(clock: Clock): ScheduleCalculator =
        ScheduleCalculator(clock, SecureRandomSource())
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchModule {
    @Binds
    abstract fun bindWatchBridge(impl: DataLayerWatchBridge): WatchBridge
}
