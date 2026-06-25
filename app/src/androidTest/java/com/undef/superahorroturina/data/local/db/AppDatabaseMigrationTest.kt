package com.undef.superahorroturina.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Ejercita literalmente el SQL de [AppDatabase.MIGRATION_5_6] contra una base SQLite real
 * (no contra tablas generadas a partir de las entidades anotadas, como hace
 * [TicketPhotoDaoTest] con `Room.inMemoryDatabaseBuilder`).
 *
 * Esa diferencia es la que importa: `inMemoryDatabaseBuilder().build()` crea el esquema
 * directamente desde las entidades de Room, así que nunca corre el `db.execSQL(...)` de la
 * migración y no puede detectar un `CREATE TABLE` mal escrito a mano (como el FOREIGN KEY que
 * faltaba antes de este fix). Este test sí ejecuta esa SQL y valida el esquema resultante.
 *
 * Como `exportSchema = false` en [AppDatabase], no hay JSON de esquemas históricos bundleados
 * como assets de test, así que no se puede usar `MigrationTestHelper.createDatabase(name, version)`
 * de la forma estándar (requiere ese asset para la versión de origen). En su lugar, se crea a mano
 * el subconjunto mínimo de esquema v5 del que depende la migración 5->6 (una tabla `purchases`
 * con `id INTEGER PRIMARY KEY`, suficiente para que el FK tenga a qué apuntar) y se corre
 * `MIGRATION_5_6.migrate(db)` directamente contra una base respaldada por archivo real.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private lateinit var dbFile: File
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = File(context.cacheDir, "migration-5-6-test.db")
        dbFile.delete()

        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Subconjunto mínimo del esquema v5: solo lo que MIGRATION_5_6 necesita
                // (la tabla `purchases` como destino del FOREIGN KEY).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // No-op: la migración se invoca manualmente en el test.
            }
        }

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(callback)
            .build()

        openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    @After
    fun tearDown() {
        openHelper.close()
        dbFile.delete()
    }

    @Test
    fun migration5a6_crea_ticket_photos_con_foreign_key_a_purchases_y_su_indice() {
        val db = openHelper.writableDatabase
        // Dispara onCreate, que deja la base en el esquema v5 mínimo de arriba.
        assertEquals(5, db.version)

        // Ejecuta literalmente la SQL de la migración bajo prueba.
        AppDatabase.MIGRATION_5_6.migrate(db)

        // La tabla nueva existe.
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='ticket_photos'").use { cursor ->
            assertTrue("la tabla ticket_photos debería existir tras la migración", cursor.moveToFirst())
        }

        // Tiene un FOREIGN KEY real hacia purchases(id) - esto es lo que el bug original rompía:
        // un CREATE TABLE sin cláusula FOREIGN KEY hace que esta pragma no devuelva filas.
        db.query("PRAGMA foreign_key_list(`ticket_photos`)").use { cursor ->
            assertTrue(
                "ticket_photos debería tener al menos un foreign key declarado",
                cursor.moveToFirst()
            )

            val tableColumnIndex = cursor.getColumnIndexOrThrow("table")
            val fromColumnIndex = cursor.getColumnIndexOrThrow("from")
            val toColumnIndex = cursor.getColumnIndexOrThrow("to")

            var foundExpectedForeignKey = false
            do {
                val referencedTable = cursor.getString(tableColumnIndex)
                val fromColumn = cursor.getString(fromColumnIndex)
                val toColumn = cursor.getString(toColumnIndex)
                if (referencedTable == "purchases" && fromColumn == "purchaseId" && toColumn == "id") {
                    foundExpectedForeignKey = true
                }
            } while (cursor.moveToNext())

            assertTrue(
                "se esperaba un FOREIGN KEY de ticket_photos.purchaseId -> purchases.id",
                foundExpectedForeignKey
            )
        }

        // El índice declarado por la migración también existe.
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_ticket_photos_purchaseId'"
        ).use { cursor ->
            assertTrue(
                "el índice index_ticket_photos_purchaseId debería existir tras la migración",
                cursor.moveToFirst()
            )
        }
    }
}
