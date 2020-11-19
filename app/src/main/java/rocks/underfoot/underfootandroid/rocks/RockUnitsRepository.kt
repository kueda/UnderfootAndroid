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
    val source: String = ""
)

class RockUnitsRepository(mbtilesPath: String) {
    companion object {
        private const val TAG = "RockUnitsRepository"
    }
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(mbtilesPath, null, 0)
    fun find(id: String): RockUnit {
        val res = db.rawQuery("SELECT * FROM rock_units_attrs WHERE id = '${id}'", null)
        res.moveToFirst()
        val rockUnit = RockUnit(
            title = res.getString(res.getColumnIndex("title")),
            description = res.getString(res.getColumnIndex("description")),
            estAge = res.getString(res.getColumnIndex("est_age")),
            minAge = res.getString(res.getColumnIndex("min_age")),
            maxAge = res.getString(res.getColumnIndex("max_age")),
            span = res.getString(res.getColumnIndex("span")),
            source = res.getString(res.getColumnIndex("source"))
        )
        res.close()
        return rockUnit
    }
}
