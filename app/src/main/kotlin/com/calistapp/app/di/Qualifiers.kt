package com.calistapp.app.di

import javax.inject.Qualifier

/** Application-lifetime coroutine scope for work that outlives any single screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
