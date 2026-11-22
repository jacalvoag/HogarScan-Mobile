package com.example.hogarscan.scan.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object HogarScanMigrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf("service_protocol", "service_vendor", "service_product", "service_version").forEach {
                db.execSQL("ALTER TABLE probe_results ADD COLUMN $it TEXT")
            }
            db.execSQL("ALTER TABLE scan_evaluations ADD COLUMN service_port INTEGER")
        }
    }


    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS scan_evaluations (
                scan_id TEXT NOT NULL, address INTEGER NOT NULL, response_json TEXT NOT NULL,
                vendor TEXT, product TEXT, version TEXT, identification_source TEXT,
                PRIMARY KEY(scan_id, address),
                FOREIGN KEY(scan_id) REFERENCES scan_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_evaluations_scan_id ON scan_evaluations(scan_id)")
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE scan_records
                ADD COLUMN summary_generation INTEGER
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_summaries (
                    scan_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    summary_generation INTEGER NOT NULL,
                    started_at_millis INTEGER NOT NULL,
                    finished_at_millis INTEGER NOT NULL,
                    addresses_scanned INTEGER NOT NULL,
                    addresses_responded INTEGER NOT NULL,
                    accepted_services INTEGER NOT NULL,
                    tcp_service_accessible INTEGER NOT NULL,
                    protocol_identified INTEGER NOT NULL,
                    preventive_review INTEGER NOT NULL,
                    possible_cve_match INTEGER NOT NULL,
                    insufficient_evidence INTEGER NOT NULL,
                    sync_status TEXT NOT NULL,
                    received_at_millis INTEGER,
                    PRIMARY KEY(scan_id),
                    FOREIGN KEY(scan_id)
                        REFERENCES scan_records(id)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                    index_scan_summaries_user_id_sync_status
                ON scan_summaries(user_id, sync_status)
                """.trimIndent()
            )
        }
    }
}
