package com.fr.husi.di

import com.fr.husi.compose.material3.DesktopPlatformMaterialApi
import com.fr.husi.compose.material3.PlatformMaterialApi
import com.fr.husi.compose.theme.PlatformThemeApi
import com.fr.husi.compose.theme.standardPlatformThemeApi
import com.fr.husi.net.GrpcHttpFetcher
import com.fr.husi.net.GrpcWebDAVClient
import com.fr.husi.net.HttpFetcher
import com.fr.husi.net.WebDAVClient
import com.fr.husi.repository.DesktopRepository
import com.fr.husi.repository.Repository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMaterialApi(): PlatformMaterialApi = DesktopPlatformMaterialApi

internal actual fun platformThemeApi(): PlatformThemeApi = standardPlatformThemeApi()

internal actual fun platformRepositoryModule(repository: Repository): Module = module {
    single<Repository> { repository }
    single<DesktopRepository> { repository as DesktopRepository }
}

private val desktopHttpModule = module {
    single<HttpFetcher> { GrpcHttpFetcher(coreClient = get()) }
    single<WebDAVClient> { GrpcWebDAVClient(coreClient = get()) }
}

internal actual fun platformKoinModules(): List<Module> = listOf(desktopHttpModule)

internal actual fun coreClientBasePath(repository: Repository): String {
    return (repository as DesktopRepository).coreSocketBasePath
}
