package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/** Marca de que hay una edición guardada (aunque tenga cero charlas). Una sola fila, id = 1. */
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 1, val guardada: Long)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("SELECT * FROM edicion WHERE id = 1")
    abstract fun observarEdicion(): Flow<Edicion?>

    @Query("DELETE FROM charlas")
    abstract suspend fun borrarCharlas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardar(fichas: List<Charla>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardarEdicion(edicion: Edicion)

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    @Query("SELECT id FROM charlas WHERE favorita = 1")
    abstract suspend fun idsFavoritas(): List<String>

    /**
     * La edición es una instantánea completa: reemplaza todo (aunque venga vacía), en una transacción.
     * Las marcas se leen ADENTRO de la transacción (no al iniciar la petición), así se respeta lo que
     * el usuario marcó o desmarcó mientras esperaba. Solo sobreviven las de charlas que siguen vigentes:
     * una charla retirada pierde su marca y, si después regresa, regresa sin marcar.
     */
    @Transaction
    open suspend fun reemplazar(fichas: List<Charla>) {
        val favoritas = idsFavoritas().toSet()
        borrarCharlas()
        guardar(fichas.map { it.copy(favorita = it.id in favoritas) })
        guardarEdicion(Edicion(guardada = System.currentTimeMillis()))
    }
}

/** v1 -> v2: agrega la tabla edicion sin tocar charlas ni favoritas. Idempotente. */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `guardada` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        // Instalaciones previas con charlas descargadas ya tienen una edición guardada.
        db.execSQL("INSERT OR IGNORE INTO `edicion` (`id`, `guardada`) SELECT 1, 0 WHERE EXISTS (SELECT 1 FROM `charlas`)")
    }
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}
