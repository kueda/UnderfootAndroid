package rocks.underfoot.underfootandroid.rocks

import android.database.sqlite.SQLiteDatabase
import android.util.Log

data class RockUnit(
    val title: String = "",
    val description: String = "",
    val estAge: String = "",
    val minAge: String = "",
    val maxAge: String = "",
    val span: String = "",
    val source: String = "",
    val citation: String = ""
)

class RockUnitsRepository(mbtilesPath: String) {
    companion object {
        private const val TAG = "RockUnitsRepository"
    }
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(mbtilesPath, null, 0)
    fun find(id: String): RockUnit {
        val res = try {
            db.rawQuery("""
                SELECT
                    rock_units_attrs.*,
                    citations.citation
                FROM rock_units_attrs
                    JOIN citations ON citations.source = rock_units_attrs.source
                WHERE id = '${id}'
            """, null)
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "Failed to query rock units: $e")
            return RockUnit()
        }
        res.moveToFirst()
        val rockUnit = RockUnit(
            title = res.getString(res.getColumnIndex("title")),
            description = res.getString(res.getColumnIndex("description")),
            estAge = res.getString(res.getColumnIndex("est_age")),
            minAge = res.getString(res.getColumnIndex("min_age")),
            maxAge = res.getString(res.getColumnIndex("max_age")),
            span = res.getString(res.getColumnIndex("span")),
            source = res.getString(res.getColumnIndex("source")),
            citation = res.getString(res.getColumnIndex("citation"))
        )
        res.close()
        return rockUnit
    }
}
