package com.plane.tracker.di

import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.plane.tracker.data.local.AppDatabase
import com.plane.tracker.data.local.FlightLogDao
import com.plane.tracker.data.local.RouteCacheDao
import com.plane.tracker.data.remote.ADSBApi
import com.plane.tracker.data.remote.ADSBDBApi
import com.plane.tracker.data.remote.HexDBApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideADSBApi(okHttp: OkHttpClient, moshi: Moshi): ADSBApi =
        Retrofit.Builder()
            .baseUrl(ADSBApi.BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(ADSBApi::class.java)

    @Provides
    @Singleton
    fun provideADSBDBApi(okHttp: OkHttpClient, moshi: Moshi): ADSBDBApi =
        Retrofit.Builder()
            .baseUrl(ADSBDBApi.BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(ADSBDBApi::class.java)

    @Provides
    @Singleton
    fun provideHexDBApi(okHttp: OkHttpClient, moshi: Moshi): HexDBApi =
        Retrofit.Builder()
            .baseUrl(HexDBApi.BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(HexDBApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "plane_tracker.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRouteCacheDao(db: AppDatabase): RouteCacheDao = db.routeCacheDao()

    @Provides
    fun provideFlightLogDao(db: AppDatabase): FlightLogDao = db.flightLogDao()

    @Provides
    @Singleton
    fun provideFusedLocationClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
}
