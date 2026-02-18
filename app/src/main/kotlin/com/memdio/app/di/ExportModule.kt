package com.memdio.app.di

import com.memdio.app.export.AacMuxerDelegate
import com.memdio.app.export.DropboxUploadClient
import com.memdio.app.export.DriveUploadClient
import com.memdio.app.export.RealAacMuxerDelegate
import com.memdio.app.export.RealDropboxUploadClient
import com.memdio.app.export.RealDriveUploadClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds @Singleton
    abstract fun bindAacMuxer(impl: RealAacMuxerDelegate): AacMuxerDelegate

    @Binds @Singleton
    abstract fun bindDriveClient(impl: RealDriveUploadClient): DriveUploadClient

    @Binds @Singleton
    abstract fun bindDropboxClient(impl: RealDropboxUploadClient): DropboxUploadClient
}
