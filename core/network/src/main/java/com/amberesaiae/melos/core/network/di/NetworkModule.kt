package com.amberesaiae.melos.core.network.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(includes = [OkHttpModule::class, RetrofitModule::class])
@InstallIn(SingletonComponent::class)
object NetworkModule
