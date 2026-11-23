package app.roundly.di

import javax.inject.Qualifier

/** Process-lifetime scope for work that must outlive a screen (housekeeping, sync). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
