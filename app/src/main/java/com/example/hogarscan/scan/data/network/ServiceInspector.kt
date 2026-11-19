package com.example.hogarscan.scan.data.network

import com.example.hogarscan.scan.domain.model.ServiceEvidence
import com.example.hogarscan.scan.domain.model.ServiceProtocol
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Inspección de hasta 8 KiB; el llamador también cierra el socket al agotar el presupuesto total. */
internal object ServiceInspector {
    const val MAX_BYTES = 8192
    const val BUDGET_MILLIS = 2000L

    fun inspect(socket: Socket, host: String, port: Int): ServiceEvidence? {
        socket.soTimeout = 700
        return when (port) {
            80 -> http(socket, host, false)
            443 -> {
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(socket, host, port, false) as SSLSocket
                tls.use {
                    it.soTimeout = 1000
                    it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    it.startHandshake()
                    http(it, host, true)
                }
            }
            else -> parseGreeting(readHeaders(socket, greeting = true))
        }
    }

    private fun http(socket: Socket, host: String, tls: Boolean): ServiceEvidence? {
        val request = "HEAD / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
        val response = readHeaders(socket, greeting = false)
        return parseGreeting(response) ?: parseHttp(response, tls)
    }

    private fun readHeaders(socket: Socket, greeting: Boolean): String {
        val buffer = ByteArrayOutputStream()
        val input = socket.getInputStream()
        while (buffer.size() < MAX_BYTES) {
            val next = input.read()
            if (next < 0) break
            buffer.write(next)
            // Las cabeceras no se conservan ni salen del teléfono.
            if (next == 10) {
                val text = buffer.toString("US-ASCII")
                if (greeting || parseGreeting(text) != null || text.endsWith("\r\n\r\n") || text.endsWith("\n\n")) return text
            }
        }
        return buffer.toString("US-ASCII")
    }

    fun parseGreeting(text: String): ServiceEvidence? {
        if ('\n' !in text) return null
        val line = text.lineSequence().firstOrNull()?.trimEnd('\r') ?: return null
        if (line.length <= 253 && Regex("SSH-(?:2\\.0|1\\.99)-[!-~]{1,200}(?: [ -~]{1,100})?").matches(line)) {
            val version = Regex("^SSH-(?:2\\.0|1\\.99)-OpenSSH_([0-9]+(?:\\.[0-9]+)+(?:p[0-9]+)?)(?:[ _].*)?$")
                .matchEntire(line)?.groupValues?.get(1)?.takeIf { it.length <= 80 }
            return if (version == null) ServiceEvidence(ServiceProtocol.SSH)
            else ServiceEvidence(ServiceProtocol.SSH, "openbsd", "openssh", version)
        }
        // Un 220 también puede ser SMTP; exigimos una indicación FTP explícita.
        if (Regex("^220[ -].*", RegexOption.IGNORE_CASE).matches(line) &&
            Regex("(?:\\bFTP\\b|vsftpd|proftpd|pure-ftpd)", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
            return ServiceEvidence(ServiceProtocol.FTP)
        }
        return null
    }

    fun parseHttp(text: String, tls: Boolean): ServiceEvidence? {
        if (!text.endsWith("\r\n\r\n") && !text.endsWith("\n\n")) return null
        val lines = text.lineSequence().toList()
        if (!Regex("HTTP/1\\.[01] [1-5][0-9]{2}(?:[ \t].*)?").matches(lines.first().trimEnd('\r'))) return null
        val protocol = if (tls) ServiceProtocol.HTTPS else ServiceProtocol.HTTP
        val servers = lines.drop(1).filter { it.substringBefore(':').lowercase(Locale.ROOT) == "server" }
        if (servers.size != 1) return ServiceEvidence(protocol)
        val value = servers.single().substringAfter(':').trim()
        val apache = Regex("Apache/([0-9]+(?:\\.[0-9]+)+)(?: \\([^\r\n]*\\))?").matchEntire(value)
        val nginx = Regex("nginx/([0-9]+(?:\\.[0-9]+)+)").matchEntire(value)
        return when {
            apache != null && apache.groupValues[1].length <= 80 -> ServiceEvidence(protocol, "apache", "http_server", apache.groupValues[1])
            nginx != null && nginx.groupValues[1].length <= 80 -> ServiceEvidence(protocol, "nginx", "nginx", nginx.groupValues[1])
            else -> ServiceEvidence(protocol)
        }
    }
}
