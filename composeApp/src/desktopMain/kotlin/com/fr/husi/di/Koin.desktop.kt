package com.fr.husi.di

import com.fr.husi.compose.material3.DesktopPlatformMaterialApi
import com.fr.husi.compose.material3.PlatformMaterialApi
import com.fr.husi.compose.theme.PlatformThemeApi
import com.fr.husi.compose.theme.standardPlatformThemeApi
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

internal actual fun platformKoinModules(): List<Module> = emptyList()

internal actual fun coreClientBasePath(repository: Repository): String? {
    return (repository as DesktopRepository).coreSocketBasePath
}
