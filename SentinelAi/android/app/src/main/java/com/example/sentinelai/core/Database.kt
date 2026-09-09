package com.example.sentinelai.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DetectionRecord(
    val id: Long,
    val filePath: String,
    val sha256: String,
    val threatName: String,
    val riskScore: Int,
    val verdict: String,
    val reasons: String,
    val source: String,
    val detectedAt: String
)

data class QuarantineRecord(
    val id: Long,
    val originalPath: String,
    val quarantinePath: String,
    val threatName: String,
    val riskScore: Int,
    val quarantinedAt: String,
    val restored: Boolean
)

fun nowIso(): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date())
}

class Database(context: Context) : SQLiteOpenHelper(context, "sentinel.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE detections (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL,
                sha256 TEXT,
                threat_name TEXT,
                risk_score INTEGER NOT NULL,
                verdict TEXT NOT NULL,
                reasons TEXT,
                source TEXT NOT NULL,
                detected_at TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE quarantine (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                original_path TEXT NOT NULL,
                quarantine_path TEXT NOT NULL,
                threat_name TEXT,
                risk_score INTEGER,
                quarantined_at TEXT NOT NULL,
                restored INTEGER DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE scan_stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                day TEXT NOT NULL,
                files_scanned INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                level TEXT NOT NULL,
                message TEXT NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS detections")
        db.execSQL("DROP TABLE IF EXISTS quarantine")
        db.execSQL("DROP TABLE IF EXISTS scan_stats")
        db.execSQL("DROP TABLE IF EXISTS events")
        onCreate(db)
    }

    fun addDetection(result: DetectionResult, source: String): Long {
        val values = ContentValues().apply {
            put("file_path", result.path)
            put("sha256", result.sha256)
            put("threat_name", result.threatName)
            put("risk_score", result.riskScore)
            put("verdict", result.verdict)
            put("reasons", result.reasons.joinToString("; "))
            put("source", source)
            put("detected_at", nowIso())
        }
        return writableDatabase.insert("detections", null, values)
    }

    fun recentDetections(limit: Int = 200): List<DetectionRecord> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM detections ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        val results = mutableListOf<DetectionRecord>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    DetectionRecord(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        filePath = it.getString(it.getColumnIndexOrThrow("file_path")),
                        sha256 = it.getString(it.getColumnIndexOrThrow("sha256")) ?: "",
                        threatName = it.getString(it.getColumnIndexOrThrow("threat_name")) ?: "",
                        riskScore = it.getInt(it.getColumnIndexOrThrow("risk_score")),
                        verdict = it.getString(it.getColumnIndexOrThrow("verdict")),
                        reasons = it.getString(it.getColumnIndexOrThrow("reasons")) ?: "",
                        source = it.getString(it.getColumnIndexOrThrow("source")),
                        detectedAt = it.getString(it.getColumnIndexOrThrow("detected_at"))
                    )
                )
            }
        }
        return results
    }

    fun recordScan(filesScanned: Int) {
        val today = nowIso().substring(0, 10)
        val values = ContentValues().apply {
            put("day", today)
            put("files_scanned", filesScanned)
        }
        writableDatabase.insert("scan_stats", null, values)
    }

    fun filesScannedToday(): Int {
        val today = nowIso().substring(0, 10)
        val cursor = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(files_scanned), 0) FROM scan_stats WHERE day = ?",
            arrayOf(today)
        )
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    fun threatsToday(): Int {
        val today = nowIso().substring(0, 10)
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM detections WHERE detected_at LIKE ? AND risk_score >= 30",
            arrayOf("$today%")
        )
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    fun addQuarantine(originalPath: String, quarantinePath: String, threatName: String, riskScore: Int): Long {
        val values = ContentValues().apply {
            put("original_path", originalPath)
            put("quarantine_path", quarantinePath)
            put("threat_name", threatName)
            put("risk_score", riskScore)
            put("quarantined_at", nowIso())
        }
        return writableDatabase.insert("quarantine", null, values)
    }

    fun listQuarantine(includeRestored: Boolean = false): List<QuarantineRecord> {
        val query = if (includeRestored) {
            "SELECT * FROM quarantine ORDER BY id DESC"
        } else {
            "SELECT * FROM quarantine WHERE restored = 0 ORDER BY id DESC"
        }
        val cursor = readableDatabase.rawQuery(query, null)
        val results = mutableListOf<QuarantineRecord>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    QuarantineRecord(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        originalPath = it.getString(it.getColumnIndexOrThrow("original_path")),
                        quarantinePath = it.getString(it.getColumnIndexOrThrow("quarantine_path")),
                        threatName = it.getString(it.getColumnIndexOrThrow("threat_name")) ?: "",
                        riskScore = it.getInt(it.getColumnIndexOrThrow("risk_score")),
                        quarantinedAt = it.getString(it.getColumnIndexOrThrow("quarantined_at")),
                        restored = it.getInt(it.getColumnIndexOrThrow("restored")) != 0
                    )
                )
            }
        }
        return results
    }

    fun quarantineCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM quarantine WHERE restored = 0", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    fun getQuarantineItem(id: Long): QuarantineRecord? =
        listQuarantine(includeRestored = true).firstOrNull { it.id == id }

    fun markQuarantineRestored(id: Long) {
        val values = ContentValues().apply { put("restored", 1) }
        writableDatabase.update("quarantine", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteQuarantineRecord(id: Long) {
        writableDatabase.delete("quarantine", "id = ?", arrayOf(id.toString()))
    }

    fun logEvent(level: String, message: String) {
        val values = ContentValues().apply {
            put("timestamp", nowIso())
            put("level", level)
            put("message", message)
        }
        writableDatabase.insert("events", null, values)
    }

    fun recentEvents(limit: Int = 100): List<Triple<String, String, String>> {
        val cursor = readableDatabase.rawQuery(
            "SELECT timestamp, level, message FROM events ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        val results = mutableListOf<Triple<String, String, String>>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(Triple(it.getString(0), it.getString(1), it.getString(2)))
            }
        }
        return results
    }
}
