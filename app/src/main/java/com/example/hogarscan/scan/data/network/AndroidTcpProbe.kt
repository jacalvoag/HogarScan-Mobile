package com.example.hogarscan.scan.data.network

import android.system.ErrnoException
import android.system.OsConstants
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.repository.TcpProbe
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class AndroidTcpProbe(
    networkRepository: AndroidWifiNetworkRepository,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TcpProbe by SocketTcpProbe(
    openSocket = networkRepository::openProbeSocket,
    connectionIsCurrent = networkRepository::isConnectionCurrent,
    classifyIOException = ::classifyAndroidIOException,
    ioDispatcher = ioDispatcher
)

private fun classifyAndroidIOException(exception: IOException): ProbeOutcome {
    var cause: Throwable? = exception
    repeat(8) {
        val errno = (cause as? ErrnoException)?.errno
        when (errno) {
            OsConstants.ECONNREFUSED -> return ProbeOutcome.REJECTED
            OsConstants.ETIMEDOUT -> return ProbeOutcome.TIMED_OUT
        }
        cause = cause?.cause
    }
    return ProbeOutcome.ERROR
}
