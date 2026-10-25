package com.fr.husi.net

import com.fr.husi.bg.withBackgroundProcess
import java.io.File

class BackgroundProcessHttpFetcher(private val delegate: HttpFetcher) : HttpFetcher {

    override suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse =
        withBackgroundProcess { delegate.fetchText(request) }

    override suspend fun download(
        request: HttpFetchRequest,
        target: File,
        onProgress: DownloadProgress,
    ) = withBackgroundProcess { delegate.download(request, target, onProgress) }
}
