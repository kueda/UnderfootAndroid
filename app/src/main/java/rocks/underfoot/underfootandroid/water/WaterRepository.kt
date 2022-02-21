package rocks.underfoot.underfootandroid.water

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import android.util.Log

class WaterRepository(mbtilesPath: String) {
    companion object {
        private const val TAG = "WaterRepository"
    }
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(mbtilesPath, null, 0)
    fun downstream(sourceId: String?): List<String> {
        if (sourceId.isNullOrBlank()) return listOf()
        val downstreamSourceIds = mutableListOf<String>()
        try {
            val cur = db.rawQuery("""
                WITH RECURSIVE segment AS (
                    SELECT DISTINCT
                      source.source_id,
                      source.from_source_id,
                      source.to_source_id
                    FROM
                      waterways_network source
                    WHERE
                      source.source_id = '$sourceId'
                    UNION
                    SELECT DISTINCT
                      downstream.source_id,
                      downstream.from_source_id,
                      downstream.to_source_id
                    FROM
                      waterways_network downstream
                        INNER JOIN segment ON segment.to_source_id = downstream.source_id
                    WHERE
                      segment.to_source_id != ''
                ) SELECT DISTINCT source_id FROM segment LIMIT 1000
            """.trimIndent(), null)
            while (cur.moveToNext()) {
                cur.getString(cur.getColumnIndex("source_id"))?.let {
                    downstreamSourceIds.add(it)
                }
            }
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "Recursive CTE test failed: $e")
        }
        return downstreamSourceIds.toList()
    }

    @SuppressLint("Range")
    fun citationForSource(source: String): String {
        val res = try {
            db.rawQuery("""
                SELECT
                    citation
                FROM citations
                WHERE source = '${source}'
            """, null)
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "Failed to find a citation for ${source}: $e")
            return ""
        }
        res.moveToFirst()
        val citation = try {
            res.getString(res.getColumnIndex("citation"))
        } catch (e: android.database.CursorIndexOutOfBoundsException) {
            "Unknown"
        }
        res.close()
        return citation
    }
}
