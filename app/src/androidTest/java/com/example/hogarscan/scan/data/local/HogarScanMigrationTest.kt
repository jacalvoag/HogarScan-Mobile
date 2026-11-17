package com.example.hogarscan.scan.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hogarscan.scan.data.local.HogarScanMigrations.MIGRATION_1_2
import com.example.hogarscan.scan.data.local.HogarScanMigrations.MIGRATION_2_3
import com.example.hogarscan.scan.domain.model.Ipv4Address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HogarScanMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HogarScanDatabase::class.java
    )

    @Test
    fun migrationAddsEvaluationsWithoutDeletingSummaries() {
        helper.createDatabase("hogarscan-migration-2-3", 2).use { db ->
            insertLegacyRecord(db, "scan-a", "user:account-a")
            db.execSQL("""INSERT INTO scan_summaries
                (scan_id, user_id, summary_generation, started_at_millis, finished_at_millis,
                 addresses_scanned, addresses_responded, accepted_services, tcp_service_accessible,
                 protocol_identified, preventive_review, possible_cve_match, insufficient_evidence, sync_status)
                VALUES ('scan-a', 'account-a', 0, 1000, 2000, 1, 1, 1, 1, 0, 0, 0, 0, 'PENDING')""".trimIndent())
        }
        helper.runMigrationsAndValidate("hogarscan-migration-2-3", 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM scan_records").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM scan_evaluations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT sync_status FROM scan_summaries WHERE scan_id = 'scan-a'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PENDING", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrationPreservesHistoryWithoutEnqueuingLegacyRecords() {
        helper.createDatabase(TEST_DATABASE, 1).use { db ->
            insertLegacyRecord(db, "old-account", "user:account-a")
            insertLegacyRecord(db, "old-guest", "local:existing-profile")
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MIGRATION_1_2
        ).use { db ->
            db.query(
                """
                SELECT id, owner_id, execution_status, summary_generation
                FROM scan_records
                ORDER BY id
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("old-account", cursor.getString(0))
                assertEquals("user:account-a", cursor.getString(1))
                assertEquals("COMPLETED", cursor.getString(2))
                assertTrue(cursor.isNull(3))

                assertTrue(cursor.moveToNext())
                assertEquals("old-guest", cursor.getString(0))
                assertEquals("local:existing-profile", cursor.getString(1))
                assertEquals("COMPLETED", cursor.getString(2))
                assertTrue(cursor.isNull(3))

                assertFalse(cursor.moveToNext())
            }

            db.query("SELECT COUNT(*) FROM scan_ports").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }

            db.query(
                """
                SELECT scan_id, outcome, duration_millis
                FROM probe_results
                ORDER BY scan_id
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("old-account", cursor.getString(0))
                assertEquals("ACCEPTED", cursor.getString(1))
                assertEquals(26L, cursor.getLong(2))

                assertTrue(cursor.moveToNext())
                assertEquals("old-guest", cursor.getString(0))
                assertEquals("ACCEPTED", cursor.getString(1))
                assertEquals(26L, cursor.getLong(2))

                assertFalse(cursor.moveToNext())
            }

            db.query("SELECT COUNT(*) FROM scan_summaries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private fun insertLegacyRecord(
        db: SupportSQLiteDatabase,
        scanId: String,
        ownerId: String
    ) {
        val localAddress = Ipv4Address.parse("192.168.1.10").value
        val targetAddress = Ipv4Address.parse("192.168.1.20").value

        db.execSQL(
            """
            INSERT INTO scan_records (
                id, owner_id, local_address, prefix_length,
                start_address, end_address, execution_status,
                started_at_millis, finished_at_millis
            ) VALUES (?, ?, ?, 24, ?, ?, 'COMPLETED', 1000, 2000)
            """.trimIndent(),
            arrayOf<Any>(
                scanId,
                ownerId,
                localAddress,
                targetAddress,
                targetAddress
            )
        )

        db.execSQL(
            "INSERT INTO scan_ports (scan_id, port) VALUES (?, 80)",
            arrayOf<Any>(scanId)
        )

        db.execSQL(
            """
            INSERT INTO probe_results (
                scan_id, address, port, outcome, duration_millis
            ) VALUES (?, ?, 80, 'ACCEPTED', 26)
            """.trimIndent(),
            arrayOf<Any>(scanId, targetAddress)
        )
    }

    private companion object {
        const val TEST_DATABASE = "hogarscan-migration-test"
    }
}
