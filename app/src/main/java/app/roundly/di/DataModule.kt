package app.roundly.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.roundly.data.db.Migrations
import app.roundly.data.db.RoundlyDatabase
import app.roundly.data.db.dao.AlarmDao
import app.roundly.data.db.dao.AlertLogDao
import app.roundly.data.db.dao.PresetDao
import app.roundly.data.repository.AlarmRepository
import app.roundly.data.repository.AlertLogRepository
import app.roundly.data.repository.DataStoreSettingsRepository
import app.roundly.data.repository.PresetRepository
import app.roundly.data.repository.RoomAlarmRepository
import app.roundly.data.repository.RoomAlertLogRepository
import app.roundly.data.repository.RoomPresetRepository
import app.roundly.data.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RoundlyDatabase =
        Room.databaseBuilder(context, RoundlyDatabase::class.java, RoundlyDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideAlarmDao(db: RoundlyDatabase): AlarmDao = db.alarmDao()

    @Provides
    fun providePresetDao(db: RoundlyDatabase): PresetDao = db.presetDao()

    @Provides
    fun provideAlertLogDao(db: RoundlyDatabase): AlertLogDao = db.alertLogDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.preferencesDataStoreFile("settings") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAlarmRepository(impl: RoomAlarmRepository): AlarmRepository

    @Binds
    abstract fun bindPresetRepository(impl: RoomPresetRepository): PresetRepository

    @Binds
    abstract fun bindAlertLogRepository(impl: RoomAlertLogRepository): AlertLogRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
