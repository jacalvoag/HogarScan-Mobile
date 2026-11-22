package com.example.hogarscan.scan.data.network

import com.example.hogarscan.scan.domain.model.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ServiceInspectorTest {
    @Test fun `solo identifica SSH cuando hay una identificacion compatible`() {
        val evidence = ServiceInspector.parseGreeting("SSH-2.0-OpenSSH_9.6p1 Ubuntu-3\r\n")!!
        assertEquals(ServiceProtocol.SSH, evidence.protocol)
        assertEquals("openbsd", evidence.vendor)
        assertEquals("9.6p1", evidence.version)
        assertNull(ServiceInspector.parseGreeting("Este es el puerto 22\r\n"))
    }

    @Test fun `un saludo SMTP 220 no se confunde con FTP`() {
        assertNull(ServiceInspector.parseGreeting("220 smtp.example ESMTP ready\r\n"))
        assertEquals(ServiceProtocol.FTP, ServiceInspector.parseGreeting("220 Welcome to FTP\r\n")?.protocol)
    }

    @Test fun `HTTP normaliza Apache solo con version explicita y cabeceras completas`() {
        val http = "HTTP/1.1 200 OK\r\nServer: Apache/2.4.49 (Unix)\r\n\r\n"
        val evidence = ServiceInspector.parseHttp(http, false)!!
        assertEquals(ServiceProtocol.HTTP, evidence.protocol)
        assertEquals("http_server", evidence.product)
        assertEquals("2.4.49", evidence.version)
        assertEquals(ServiceProtocol.HTTPS, ServiceInspector.parseHttp(http, true)?.protocol)
        assertNull(ServiceInspector.parseHttp(http.trimEnd(), false))
        assertNull(ServiceInspector.parseHttp("Una pagina web\r\n\r\n", false))
    }

    @Test fun `cabeceras ambiguas no inventan producto ni version`() {
        assertNull(ServiceInspector.parseHttp("HTTP/1.0 403 Forbidden\r\nServer: Apache\r\n\r\n", false)!!.product)
        assertNull(ServiceInspector.parseHttp("HTTP/1.1 200 OK\r\nServer: Apache/2.4.49\r\nServer: nginx/1.2.3\r\n\r\n", false)!!.product)
    }

    @Test fun `versiones excesivas y saludos truncados no fabrican identidad`() {
        assertNull(ServiceInspector.parseGreeting("SSH-2.0-OpenSSH_9.6"))
        val oversized = "HTTP/1.1 200 OK\r\nServer: Apache/2." + "4".repeat(90) + "\r\n\r\n"
        assertNull(ServiceInspector.parseHttp(oversized, false)!!.version)
    }

    @Test fun `conexion real envia HEAD y conserva identidad normalizada sin banner`() = runBlocking {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            var request = ""
            val worker = Thread {
                server.accept().use { client ->
                    request = client.getInputStream().bufferedReader().readLine()
                    client.getOutputStream().write("HTTP/1.1 200 OK\r\nServer: Apache/2.4.49\r\n\r\n".toByteArray())
                }
            }.apply { isDaemon = true; start() }
            val socket = object : Socket() {
                override fun connect(endpoint: SocketAddress, timeout: Int) {
                    super.connect(InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), server.localPort), timeout)
                }
            }
            val probe = SocketTcpProbe({ _, _ -> socket }, { true }, { ProbeOutcome.ERROR }, Dispatchers.IO)
            val result = probe.probe("test", Ipv4Address.parse("192.168.1.2"), 80)
            worker.join(1000)
            assertEquals("HEAD / HTTP/1.1", request)
            assertEquals(ProbeOutcome.ACCEPTED, result.outcome)
            assertEquals("2.4.49", result.service?.version)
            assertTrue(socket.isClosed)
        }
    }

    @Test fun `un receptor TCP silencioso no se convierte en HTTP ni pierde el estado aceptado`() = runBlocking {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val socket = object : Socket() {
                override fun connect(endpoint: SocketAddress, timeout: Int) {
                    super.connect(InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), server.localPort), timeout)
                }
            }
            val result = SocketTcpProbe({ _, _ -> socket }, { true }, { ProbeOutcome.ERROR }, Dispatchers.IO)
                .probe("test", Ipv4Address.parse("192.168.1.2"), 80)
            assertEquals(ProbeOutcome.ACCEPTED, result.outcome)
            assertNull(result.service)
            assertTrue(result.durationMillis < 3000)
        }
    }
}
