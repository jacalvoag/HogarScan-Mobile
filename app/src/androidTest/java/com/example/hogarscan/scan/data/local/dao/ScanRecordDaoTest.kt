package com.example.hogarscan.scan.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.data.local.entity.ProbeResultEntity
import com.example.hogarscan.scan.data.local.entity.ScanPortEntity
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity
import com.example.hogarscan.scan.data.local.relation.ScanRecordWithDetails
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanRecordDaoTest {

    private lateinit var database: HogarScanDatabase
    private lateinit var dao: ScanRecordDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            HogarScanDatabase::class.java
        ).build()

        dao = database.scanRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertWithDetails_restoresRecordPortsAndResults() = runBlocking {
        val expected = fixture()

        dao.insertWithDetails(expected)

        val actual = checkNotNull(
            dao.findById(expected.record.id, expected.record.ownerId)
        )

        assertEquals(expected.record, actual.record)
        assertEquals(
            expected.ports.sortedBy { it.port },
            actual.ports.sortedBy { it.port }
        )
        assertEquals(
            expected.results.sortedBy { it.port },
            actual.results.sortedBy { it.port }
        )
    }

    @Test
    fun insertWithDetails_rollsBackWhenResultUsesUnselectedPort() = runBlocking {
        val valid = fixture()
        val invalid = valid.copy(
            results = listOf(
                valid.results.first(),
                valid.results.last().copy(port = 22)
            )
        )

        try {
            dao.insertWithDetails(invalid)
            fail("Debe rechazarse un resultado para un puerto no seleccionado.")
        } catch (_: SQLiteConstraintException) {
            // El puerto 22 no pertenece a la configuración guardada.
        }

        assertNull(dao.findById(valid.record.id, valid.record.ownerId))

        val records = withTimeout(5_000L) {
            dao.observeByOwner(valid.record.ownerId).first()
        }
        assertEquals(emptyList<ScanRecordEntity>(), records)

        // Reutilizar el mismo ID también comprueba que no quedaron filas hijas.
        dao.insertWithDetails(valid)

        val restored = checkNotNull(
            dao.findById(valid.record.id, valid.record.ownerId)
        )
        assertEquals(valid.ports.sortedBy { it.port }, restored.ports.sortedBy { it.port })
        assertEquals(valid.results.sortedBy { it.port }, restored.results.sortedBy { it.port })
    }

    @Test
    fun queries_filterByOwnerAndOrderNewestFirst() = runBlocking {
        val older = fixture("older", "owner-a", 1_000L)
        val newer = fixture("newer", "owner-a", 2_000L)
        val otherOwner = fixture("other", "owner-b", 3_000L)

        dao.insertWithDetails(older)
        dao.insertWithDetails(newer)
        dao.insertWithDetails(otherOwner)

        val records = withTimeout(5_000L) {
            dao.observeByOwner("owner-a").first()
        }

        assertEquals(listOf(newer.record, older.record), records)
        assertNull(dao.findById(otherOwner.record.id, "owner-a"))
        assertNull(dao.findById("missing", "owner-a"))
    }

    private fun fixture(
        id: String = "scan-1",
        ownerId: String = "owner-a",
        startedAtMillis: Long = 1_000L
    ): ScanRecordWithDetails {
        val target = Ipv4Address.parse("192.168.1.20").value

        return ScanRecordWithDetails(
            record = ScanRecordEntity(
                id = id,
                ownerId = ownerId,
                localAddress = Ipv4Address.parse("192.168.1.10").value,
                prefixLength = 24,
                startAddress = target,
                endAddress = target,
                status = ScanRecordStatus.COMPLETED,
                startedAtMillis = startedAtMillis,
                finishedAtMillis = startedAtMillis + 2_000L
            ),
            ports = listOf(
                ScanPortEntity(scanId = id, port = 80),
                ScanPortEntity(scanId = id, port = 443)
            ),
            results = listOf(
                ProbeResultEntity(
                    scanId = id,
                    address = target,
                    port = 80,
                    outcome = ProbeOutcome.ACCEPTED,
                    durationMillis = 26L
                ),
                ProbeResultEntity(
                    scanId = id,
                    address = target,
                    port = 443,
                    outcome = ProbeOutcome.TIMED_OUT,
                    durationMillis = 1_000L
                )
            )
        )
    }
}