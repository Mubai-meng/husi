package com.fr.husi.di

import com.fr.husi.bg.ServiceEventMirror
import com.fr.husi.compose.material3.PlatformMaterialApi
import com.fr.husi.compose.material3.TvPlatformMaterialApi
import com.fr.husi.compose.material3.standardPlatformMaterialApi
import com.fr.husi.compose.theme.PlatformThemeApi
import com.fr.husi.compose.theme.TvPlatformThemeApi
import com.fr.husi.compose.theme.standardPlatformThemeApi
import com.fr.husi.repository.AndroidRepository
import com.fr.husi.repository.Repository
import com.fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMaterialApi(): PlatformMaterialApi {
    return if (resolveRepository().isTv) {
        TvPlatformMaterialApi
    } else {
        standardPlatformMaterialApi()
    }
}

internal actual fun platformThemeApi(): PlatformThemeApi {
    return if (resolveRepository().isTv) {
        TvPlatformThemeApi
    } else {
        standardPlatformThemeApi()
    }
}

internal actual fun platformRepositoryModule(repository: Repository): Module = module {
    val androidRepository = repository as? AndroidRepository
        ?: error("Android platform requires AndroidRepository, got ${repository::class.qualifiedName}")
    single<AndroidRepository> { androidRepository }
    single<Repository> { get<AndroidRepository>() }
    if (repository.isMainProcess) {
        single {
            ServiceEventMirror(
                coreClient = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
    }
}

internal actual fun platformKoinModules(): List<Module> = listOf(androidNavigationModule)

internal actual fun coreClientBasePath(repository: Repository): String? = null
