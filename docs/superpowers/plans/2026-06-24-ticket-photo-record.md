# Foto del ticket como registro + elección manual/IA — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persistir la(s) foto(s) del ticket como parte del registro de la compra, y separar "sacar la foto" de "decidir cómo cargar los productos" (manual o con ayuda de la IA), con una barra de progreso a pantalla completa mientras la IA procesa.

**Architecture:** Nueva entidad Room `TicketPhotoEntity` (1-a-muchos con `PurchaseEntity`, cascade delete) + un helper de archivos puro (`TicketPhotoStorage`, sin dependencias de Android, testeable con JUnit normal) que guarda los bytes ya comprimidos en `filesDir/ticket_photos/<purchaseId>/`. Un nuevo `TicketPhotoRepository` orquesta Dao + Storage. El flujo de `PurchaseDetailScreen` cambia: confirmar las fotos staged ya no escanea de inmediato — las persiste, y desde ahí dos botones ("Cargar manualmente" / "Ayudame con IA") quedan disponibles en cualquier momento mientras la compra tenga fotos guardadas.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.6.1, Hilt, Coil (ya en el proyecto). Sin cambios de backend.

**Spec:** `docs/superpowers/specs/2026-06-24-ticket-photo-record-design.md`

---

## Mapa de archivos

- Crear: `app/src/main/java/com/undef/superahorroturina/data/local/db/TicketPhotoEntity.kt`
- Crear: `app/src/main/java/com/undef/superahorroturina/data/local/db/TicketPhotoDao.kt`
- Modificar: `app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt` (entidad nueva, versión 6, `MIGRATION_5_6`, `ticketPhotoDao()`)
- Modificar: `app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt` (migración + provider del DAO)
- Crear: `app/src/main/java/com/undef/superahorroturina/data/local/TicketPhotoStorage.kt`
- Crear: `app/src/main/java/com/undef/superahorroturina/data/repository/TicketPhotoRepository.kt`
- Modificar: `app/src/main/java/com/undef/superahorroturina/data/repository/PurchaseRepository.kt` (cleanup de fotos al borrar compra)
- Modificar: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailViewModel.kt` (reemplaza `scanTicketFromUris` por `savePhotosForPurchase` + `scanTicketFromSavedPhotos`)
- Crear: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketPhotoStrip.kt`
- Crear: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketScanningOverlay.kt`
- Modificar: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailScreen.kt` (nuevo flujo de `TicketAttachCard`)
- Modificar: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketPhotosPreviewScreen.kt` (el botón de confirmar pasa a guardar, no a escanear)
- Modificar: `app/src/main/res/values/strings.xml` y `app/src/main/res/values-en/strings.xml` (strings nuevos)
- Modificar: `gradle/libs.versions.toml` y `app/build.gradle.kts` (dependencia `room-testing` para el test del DAO)
- Test: `app/src/test/java/com/undef/superahorroturina/data/local/TicketPhotoStorageTest.kt`
- Test: `app/src/androidTest/java/com/undef/superahorroturina/data/local/db/TicketPhotoDaoTest.kt`

---

### Task 1: Dependencia `room-testing`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Agregar la librería al catálogo de versiones**

En `gradle/libs.versions.toml`, en la sección `[libraries]`, agregar esta línea inmediatamente después de la línea `room-compiler = ...` (línea 55):

```toml
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

- [ ] **Step 2: Agregar la dependencia de test**

En `app/build.gradle.kts`, dentro del bloque `dependencies { ... }`, agregar esta línea inmediatamente después de `androidTestImplementation(libs.androidx.espresso.core)` (línea 85):

```kotlin
    androidTestImplementation(libs.room.testing)
```

- [ ] **Step 3: Verificar que sincroniza**

Run: `./gradlew :app:assembleDebugAndroidTest` (en Windows: `gradlew.bat :app:assembleDebugAndroidTest`)
Expected: `BUILD SUCCESSFUL` (puede tardar varios minutos la primera vez).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: agregar room-testing para tests de DAO"
```

---

### Task 2: `TicketPhotoEntity` + `TicketPhotoDao` + migración de Room

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/data/local/db/TicketPhotoEntity.kt`
- Create: `app/src/main/java/com/undef/superahorroturina/data/local/db/TicketPhotoDao.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt`
- Test: `app/src/androidTest/java/com/undef/superahorroturina/data/local/db/TicketPhotoDaoTest.kt`

- [ ] **Step 1: Crear la entidad**

`app/src/main/java/com/undef/superahorroturina/data/local/db/TicketPhotoEntity.kt`:

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ticket_photos",
    foreignKeys = [ForeignKey(
        entity = PurchaseEntity::class,
        parentColumns = ["id"],
        childColumns = ["purchaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("purchaseId")]
)
data class TicketPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Int,
    val filePath: String,
    val displayOrder: Int,
    val capturedAt: Long
)
```

- [ ] **Step 2: Crear el DAO**

`app/src/main/java/com/undef/superahorroturina/data/local/db/TicketPhotoDao.kt`:

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketPhotoDao {
    @Insert
    suspend fun insertAll(photos: List<TicketPhotoEntity>)

    @Query("SELECT * FROM ticket_photos WHERE purchaseId = :purchaseId ORDER BY displayOrder ASC")
    fun getByPurchaseId(purchaseId: Int): Flow<List<TicketPhotoEntity>>

    @Query("SELECT * FROM ticket_photos WHERE purchaseId = :purchaseId ORDER BY displayOrder ASC")
    suspend fun getByPurchaseIdOnce(purchaseId: Int): List<TicketPhotoEntity>

    @Query("DELETE FROM ticket_photos WHERE purchaseId = :purchaseId")
    suspend fun deleteByPurchaseId(purchaseId: Int)
}
```

- [ ] **Step 3: Escribir el test del DAO (en rojo)**

`app/src/androidTest/java/com/undef/superahorroturina/data/local/db/TicketPhotoDaoTest.kt`:

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TicketPhotoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var purchaseDao: PurchaseDao
    private lateinit var ticketPhotoDao: TicketPhotoDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        purchaseDao = db.purchaseDao()
        ticketPhotoDao = db.ticketPhotoDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun samplePurchase(id: Int) = PurchaseEntity(
        id = id,
        purchaseDate = "2026-06-24",
        purchaseTime = "10:00:00",
        supermarket = "Coto",
        total = 100.0,
        productCount = 0
    )

    @Test
    fun insertAll_y_getByPurchaseId_devuelve_las_fotos_ordenadas() = runBlocking {
        purchaseDao.upsert(samplePurchase(1))
        ticketPhotoDao.insertAll(
            listOf(
                TicketPhotoEntity(purchaseId = 1, filePath = "/foo/2.jpg", displayOrder = 1, capturedAt = 1000L),
                TicketPhotoEntity(purchaseId = 1, filePath = "/foo/1.jpg", displayOrder = 0, capturedAt = 1000L)
            )
        )

        val result = ticketPhotoDao.getByPurchaseId(1).first()

        assertEquals(2, result.size)
        assertEquals("/foo/1.jpg", result[0].filePath)
        assertEquals("/foo/2.jpg", result[1].filePath)
    }

    @Test
    fun borrar_la_compra_borra_sus_fotos_por_cascade() = runBlocking {
        purchaseDao.upsert(samplePurchase(1))
        ticketPhotoDao.insertAll(
            listOf(TicketPhotoEntity(purchaseId = 1, filePath = "/foo/1.jpg", displayOrder = 0, capturedAt = 1000L))
        )

        purchaseDao.delete(1)

        val result = ticketPhotoDao.getByPurchaseId(1).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun deleteByPurchaseId_borra_solo_las_fotos_de_esa_compra() = runBlocking {
        purchaseDao.upsert(samplePurchase(1))
        purchaseDao.upsert(samplePurchase(2))
        ticketPhotoDao.insertAll(
            listOf(
                TicketPhotoEntity(purchaseId = 1, filePath = "/foo/1.jpg", displayOrder = 0, capturedAt = 1000L),
                TicketPhotoEntity(purchaseId = 2, filePath = "/foo/2.jpg", displayOrder = 0, capturedAt = 1000L)
            )
        )

        ticketPhotoDao.deleteByPurchaseId(1)

        assertTrue(ticketPhotoDao.getByPurchaseId(1).first().isEmpty())
        assertEquals(1, ticketPhotoDao.getByPurchaseId(2).first().size)
    }
}
```

Run: `gradlew.bat :app:connectedDebugAndroidTest --tests "com.undef.superahorroturina.data.local.db.TicketPhotoDaoTest"`
Expected: FAIL (no compila — `AppDatabase` todavía no tiene `ticketPhotoDao()` ni conoce la entidad).

- [ ] **Step 4: Registrar la entidad, subir la versión y agregar la migración**

En `app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt`, reemplazar todo el archivo:

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PurchaseEntity::class, ProductEntity::class, SupermarketEntity::class, PriceComparisonEntity::class, TicketPhotoEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
    abstract fun productDao(): ProductDao
    abstract fun supermarketDao(): SupermarketDao
    abstract fun priceComparisonDao(): PriceComparisonDao
    abstract fun ticketPhotoDao(): TicketPhotoDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN seedProductName TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS supermarkets (name TEXT NOT NULL PRIMARY KEY)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS price_comparisons (
                        productName TEXT NOT NULL PRIMARY KEY,
                        brand TEXT NOT NULL,
                        category TEXT NOT NULL,
                        pricesJson TEXT NOT NULL,
                        cheapestAt TEXT NOT NULL,
                        cheapestPrice REAL NOT NULL,
                        maxSavings REAL NOT NULL,
                        savingsPct INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS ticket_photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        purchaseId INTEGER NOT NULL,
                        filePath TEXT NOT NULL,
                        displayOrder INTEGER NOT NULL,
                        capturedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ticket_photos_purchaseId ON ticket_photos(purchaseId)")
            }
        }
    }
}
```

- [ ] **Step 5: Registrar la migración y el DAO en Hilt**

En `app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt`, reemplazar todo el archivo:

```kotlin
package com.undef.superahorroturina.di

import android.content.Context
import androidx.room.Room
import com.undef.superahorroturina.data.local.db.AppDatabase
import com.undef.superahorroturina.data.local.db.PriceComparisonDao
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.PurchaseDao
import com.undef.superahorroturina.data.local.db.SupermarketDao
import com.undef.superahorroturina.data.local.db.TicketPhotoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "klarity_db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePurchaseDao(db: AppDatabase): PurchaseDao = db.purchaseDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun provideSupermarketDao(db: AppDatabase): SupermarketDao = db.supermarketDao()

    @Provides
    fun providePriceComparisonDao(db: AppDatabase): PriceComparisonDao = db.priceComparisonDao()

    @Provides
    fun provideTicketPhotoDao(db: AppDatabase): TicketPhotoDao = db.ticketPhotoDao()
}
```

- [ ] **Step 6: Correr el test y verificar que pasa**

Run: `gradlew.bat :app:connectedDebugAndroidTest --tests "com.undef.superahorroturina.data.local.db.TicketPhotoDaoTest"`
Expected: 3 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/local/db/TicketPhotoEntity.kt app/src/main/java/com/undef/superahorroturina/data/local/db/TicketPhotoDao.kt app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt app/src/androidTest/java/com/undef/superahorroturina/data/local/db/TicketPhotoDaoTest.kt
git commit -m "feat: entidad y DAO de fotos de ticket, migracion 5->6"
```

---

### Task 3: `TicketPhotoStorage` (helper de archivos puro)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/data/local/TicketPhotoStorage.kt`
- Test: `app/src/test/java/com/undef/superahorroturina/data/local/TicketPhotoStorageTest.kt`

- [ ] **Step 1: Escribir el test (en rojo)**

`app/src/test/java/com/undef/superahorroturina/data/local/TicketPhotoStorageTest.kt`:

```kotlin
package com.undef.superahorroturina.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TicketPhotoStorageTest {

    @Test
    fun `savePhoto escribe los bytes en filesDir-ticket_photos-purchaseId y devuelve la ruta`() {
        val baseDir = createTempDir()
        val bytes = byteArrayOf(1, 2, 3, 4)

        val path = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 7, bytes = bytes)

        val file = File(path)
        assertTrue(file.exists())
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(file.parentFile!!.path.replace('\\', '/').endsWith("ticket_photos/7"))
    }

    @Test
    fun `dos llamadas a savePhoto para la misma compra generan archivos distintos`() {
        val baseDir = createTempDir()

        val path1 = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 1, bytes = byteArrayOf(1))
        val path2 = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 1, bytes = byteArrayOf(2))

        assertTrue(path1 != path2)
        assertTrue(File(path1).exists())
        assertTrue(File(path2).exists())
    }

    @Test
    fun `deletePurchasePhotos borra el directorio de esa compra`() {
        val baseDir = createTempDir()
        val path = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 3, bytes = byteArrayOf(9))

        TicketPhotoStorage.deletePurchasePhotos(baseDir, purchaseId = 3)

        assertFalse(File(path).exists())
    }

    @Test
    fun `deletePurchasePhotos no afecta los archivos de otra compra`() {
        val baseDir = createTempDir()
        val keepPath = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 1, bytes = byteArrayOf(1))
        TicketPhotoStorage.savePhoto(baseDir, purchaseId = 2, bytes = byteArrayOf(2))

        TicketPhotoStorage.deletePurchasePhotos(baseDir, purchaseId = 2)

        assertTrue(File(keepPath).exists())
    }
}
```

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.undef.superahorroturina.data.local.TicketPhotoStorageTest"`
Expected: FAIL (no compila — `TicketPhotoStorage` no existe).

- [ ] **Step 2: Implementar `TicketPhotoStorage`**

`app/src/main/java/com/undef/superahorroturina/data/local/TicketPhotoStorage.kt`:

```kotlin
// TicketPhotoStorage.kt — guarda en disco las fotos de ticket ya comprimidas y limpia los
// archivos de una compra cuando se la borra. No depende de Context/Android a propósito: recibe
// el directorio base (en producción, context.filesDir) para poder testearlo con JUnit normal,
// sin Robolectric ni un dispositivo/emulador.
package com.undef.superahorroturina.data.local

import java.io.File
import java.util.UUID

object TicketPhotoStorage {
    private const val DIR_NAME = "ticket_photos"

    fun savePhoto(baseDir: File, purchaseId: Int, bytes: ByteArray): String {
        val dir = purchaseDir(baseDir, purchaseId).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun deletePurchasePhotos(baseDir: File, purchaseId: Int) {
        purchaseDir(baseDir, purchaseId).deleteRecursively()
    }

    private fun purchaseDir(baseDir: File, purchaseId: Int): File =
        File(baseDir, "$DIR_NAME/$purchaseId")
}
```

- [ ] **Step 3: Correr el test y verificar que pasa**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.undef.superahorroturina.data.local.TicketPhotoStorageTest"`
Expected: 4 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/local/TicketPhotoStorage.kt app/src/test/java/com/undef/superahorroturina/data/local/TicketPhotoStorageTest.kt
git commit -m "feat: TicketPhotoStorage para persistir fotos de ticket en disco"
```

---

### Task 4: `TicketPhotoRepository`

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/data/repository/TicketPhotoRepository.kt`

- [ ] **Step 1: Implementar el repositorio**

`app/src/main/java/com/undef/superahorroturina/data/repository/TicketPhotoRepository.kt`:

```kotlin
// TicketPhotoRepository.kt — junta TicketPhotoDao (filas en Room) y TicketPhotoStorage
// (archivos en disco) detrás de una sola API para el ViewModel. Las fotos de ticket son
// puramente locales (no se sincronizan con el backend), por eso no hay llamadas a ApiService aquí.
package com.undef.superahorroturina.data.repository

import android.content.Context
import com.undef.superahorroturina.data.local.TicketPhotoStorage
import com.undef.superahorroturina.data.local.db.TicketPhotoDao
import com.undef.superahorroturina.data.local.db.TicketPhotoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketPhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ticketPhotoDao: TicketPhotoDao
) {
    fun getPhotos(purchaseId: Int): Flow<List<TicketPhotoEntity>> =
        ticketPhotoDao.getByPurchaseId(purchaseId)

    suspend fun getPhotosOnce(purchaseId: Int): List<TicketPhotoEntity> =
        ticketPhotoDao.getByPurchaseIdOnce(purchaseId)

    suspend fun savePhotos(purchaseId: Int, photoBytes: List<ByteArray>) {
        val now = System.currentTimeMillis()
        val entities = photoBytes.mapIndexed { index, bytes ->
            val path = TicketPhotoStorage.savePhoto(context.filesDir, purchaseId, bytes)
            TicketPhotoEntity(purchaseId = purchaseId, filePath = path, displayOrder = index, capturedAt = now)
        }
        ticketPhotoDao.insertAll(entities)
    }

    suspend fun deletePhotosForPurchase(purchaseId: Int) {
        ticketPhotoDao.deleteByPurchaseId(purchaseId)
        TicketPhotoStorage.deletePurchasePhotos(context.filesDir, purchaseId)
    }
}
```

- [ ] **Step 2: Verificar que compila**

Run: `gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/repository/TicketPhotoRepository.kt
git commit -m "feat: TicketPhotoRepository"
```

---

### Task 5: Limpiar las fotos al borrar una compra

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/repository/PurchaseRepository.kt:23-28` (constructor) y `:90-99` (`deletePurchase`)

- [ ] **Step 1: Inyectar `TicketPhotoRepository` y borrar las fotos al borrar la compra**

En `app/src/main/java/com/undef/superahorroturina/data/repository/PurchaseRepository.kt`, reemplazar el constructor (líneas 22-28):

```kotlin
@Singleton
class PurchaseRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao,
    private val ticketPhotoRepository: TicketPhotoRepository
) {
```

Agregar el import correspondiente junto a los demás imports del archivo (después de la línea `import com.undef.superahorroturina.data.network.ApiService`):

```kotlin
import com.undef.superahorroturina.data.repository.TicketPhotoRepository
```

Reemplazar `deletePurchase` (líneas 90-99):

```kotlin
    suspend fun deletePurchase(id: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.deletePurchase(token, id)
        if (response.isSuccessful) {
            purchaseDao.delete(id)
            ticketPhotoRepository.deletePhotosForPurchase(id)
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al eliminar compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }
```

- [ ] **Step 2: Verificar que compila**

Run: `gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. (`TicketPhotoRepository` ya es inyectable por Hilt desde la Task 4, no hace falta tocar ningún módulo de DI para esto.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/repository/PurchaseRepository.kt
git commit -m "fix: borrar las fotos del ticket al borrar la compra"
```

---

### Task 6: Strings nuevos (es + en)

**Files:**
- Modify: `app/src/main/res/values/strings.xml:212` (después de `ticket_photo_remove`)
- Modify: `app/src/main/res/values-en/strings.xml` (mismas keys, equivalente en inglés, mismo punto de inserción)

- [ ] **Step 1: Agregar las strings en español**

En `app/src/main/res/values/strings.xml`, inmediatamente después de la línea 212 (`<string name="ticket_photo_remove">Quitar foto %1$d</string>`), agregar:

```xml
    <string name="ticket_photos_saved_title">Fotos del ticket</string>
    <string name="action_save_photos">Guardar fotos (%1$d)</string>
    <string name="ticket_choice_manual">Cargar manualmente</string>
    <string name="ticket_choice_ai">Ayudame con IA</string>
    <string name="ticket_scanning_overlay_text">Analizando tu ticket con IA, esto puede tardar unos segundos…</string>
    <string name="ticket_photo_viewer_title">Foto %1$d de %2$d</string>
```

- [ ] **Step 2: Agregar el equivalente en inglés**

En `app/src/main/res/values-en/strings.xml`, ubicar la línea con `ticket_photo_remove` (mismo número de línea aproximado, 212) y agregar inmediatamente después:

```xml
    <string name="ticket_photos_saved_title">Receipt photos</string>
    <string name="action_save_photos">Save photos (%1$d)</string>
    <string name="ticket_choice_manual">Enter manually</string>
    <string name="ticket_choice_ai">Help me with AI</string>
    <string name="ticket_scanning_overlay_text">Analyzing your receipt with AI, this might take a few seconds…</string>
    <string name="ticket_photo_viewer_title">Photo %1$d of %2$d</string>
```

- [ ] **Step 3: Verificar que el recurso resuelve**

Run: `gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (Android lint de recursos corre en el build completo, pero un mismatch de `%1$d`/`%2$d` entre idiomas rompe el build de `assembleDebug`; si se quiere chequear explícitamente: `gradlew.bat :app:lintDebug` y revisar que no haya `MissingTranslation` para estas keys).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "i18n: strings para el flujo de fotos de ticket guardadas"
```

---

### Task 7: ViewModel — `savePhotosForPurchase` y `scanTicketFromSavedPhotos`

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailViewModel.kt`

- [ ] **Step 1: Reemplazar el flujo de escaneo**

Reemplazar todo el archivo `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailViewModel.kt`:

```kotlin
// ViewModel para el detalle de una compra.
// Carga la compra con sus productos desde el backend.
// También maneja el flujo de fotos de ticket: guardarlas como registro de la compra,
// y desde ahí cargar los productos a mano o pedirle ayuda a la IA (escanear → matchear
// contra la seed → confirmar inserción).
package com.undef.superahorroturina.ui.screens.purchase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.TicketPhotoEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.ScannedProductDto
import com.undef.superahorroturina.data.network.dto.ScanTicketRequest
import com.undef.superahorroturina.data.network.dto.SeedSearchResultDto
import com.undef.superahorroturina.data.network.dto.TicketImageDto
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.ProductRepository
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.data.repository.TicketPhotoRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PurchaseDetailUiState(
    val isLoading: Boolean = true,
    val purchase: Purchase? = null,
    val error: String = ""
)

// Un producto detectado en el ticket junto con su estado de vínculo a la seed.
// seedMatch = nombre exacto de reference_prices.product_name, o null si no está vinculado.
data class ScannedProductUi(
    val product: ScannedProductDto,
    val seedMatch: String? = null,
    val seedCandidates: List<String> = emptyList()
)

// Estado del flujo de escaneo de ticket
sealed class TicketScanState {
    object Idle : TicketScanState()
    object Scanning : TicketScanState()
    data class Confirm(val items: List<ScannedProductUi>, val supermarket: String?) : TicketScanState()
    object Inserting : TicketScanState()
    data class Error(val message: String) : TicketScanState()
    object Done : TicketScanState()
}

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val productRepository: ProductRepository,
    private val ticketPhotoRepository: TicketPhotoRepository,
    private val api: ApiService,
    private val session: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseDetailUiState())
    val uiState: StateFlow<PurchaseDetailUiState> = _uiState.asStateFlow()

    private val _ticketScanState = MutableStateFlow<TicketScanState>(TicketScanState.Idle)
    val ticketScanState: StateFlow<TicketScanState> = _ticketScanState.asStateFlow()

    fun resetTicketScan() { _ticketScanState.value = TicketScanState.Idle }

    // Fotos del ticket ya guardadas como registro de esta compra (Room, reactivo).
    fun ticketPhotosFlow(purchaseId: Int): Flow<List<TicketPhotoEntity>> =
        ticketPhotoRepository.getPhotos(purchaseId)

    fun loadPurchase(purchaseId: Int) {
        viewModelScope.launch {
            _uiState.value = PurchaseDetailUiState(isLoading = true)
            when (val result = purchaseRepository.getPurchase(purchaseId)) {
                is ApiResult.Success -> _uiState.value = PurchaseDetailUiState(
                    isLoading = false,
                    purchase  = result.data
                )
                is ApiResult.Error   -> _uiState.value = PurchaseDetailUiState(
                    isLoading = false,
                    error     = result.message
                )
            }
        }
    }

    fun deletePurchase(onSuccess: () -> Unit) {
        val id = _uiState.value.purchase?.id ?: return
        viewModelScope.launch {
            purchaseRepository.deletePurchase(id)
            onSuccess()
        }
    }

    fun deleteProduct(purchaseId: Int, productId: Int) {
        viewModelScope.launch {
            productRepository.deleteProduct(purchaseId, productId)
            // Recargar la compra para reflejar el nuevo total y lista de productos
            loadPurchase(purchaseId)
        }
    }

    // ── Fotos del ticket ──────────────────────────────────────────
    // Guarda las fotos staged como registro permanente de la compra. No escanea nada todavía:
    // eso es una decisión separada que el usuario toma después (botón "Ayudame con IA").
    fun savePhotosForPurchase(context: Context, imageUris: List<Uri>, purchaseId: Int) {
        viewModelScope.launch {
            try {
                val photoBytes = imageUris.map { uri ->
                    resizeImageForUpload(context, uri)
                        ?: run {
                            _ticketScanState.value = TicketScanState.Error("No se pudo leer la imagen")
                            return@launch
                        }
                }
                ticketPhotoRepository.savePhotos(purchaseId, photoBytes)
            } catch (e: Exception) {
                _ticketScanState.value = TicketScanState.Error("No se pudieron guardar las fotos: ${e.message}")
            }
        }
    }

    // ── Ticket OCR ────────────────────────────────────────────────
    // Antes había un fallback silencioso a ML Kit (OCR de texto crudo + un regex de precios) si
    // Gemini fallaba por cualquier motivo — eso convertía cualquier error transitorio (un 503 de
    // Gemini, un redeploy del backend, una mala conexión) en una pantalla de confirmación con
    // basura que parecía un escaneo exitoso. Ahora reintenta la llamada real un par de veces y,
    // si de verdad no se pudo escanear, lo dice — no inventa productos falsos.
    // Lee las fotos ya persistidas (guardadas por savePhotosForPurchase) en vez de URIs
    // transitorias — el usuario puede pedir esto en cualquier momento, no solo justo después
    // de sacar la foto.
    fun scanTicketFromSavedPhotos(purchaseId: Int) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Scanning
            try {
                val savedPhotos = ticketPhotoRepository.getPhotosOnce(purchaseId)
                if (savedPhotos.isEmpty()) {
                    _ticketScanState.value = TicketScanState.Error("No hay fotos del ticket guardadas para escanear")
                    return@launch
                }

                val images = savedPhotos.map { photo ->
                    val bytes = File(photo.filePath).readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    TicketImageDto(base64, "image/jpeg")
                }

                val token = session.bearerToken.first()
                val request = ScanTicketRequest(images)

                val maxAttempts = 3
                for (attempt in 1..maxAttempts) {
                    val response = try {
                        api.scanTicket(token, purchaseId, request)
                    } catch (e: Exception) {
                        if (attempt == maxAttempts) {
                            _ticketScanState.value = TicketScanState.Error(
                                "No se pudo conectar para escanear el ticket. Revisá tu conexión e intentá de nuevo."
                            )
                            return@launch
                        }
                        delay(attempt * 1500L)
                        continue
                    }

                    if (response.isSuccessful) {
                        val body = response.body()
                        val products = body?.products ?: emptyList()
                        if (products.isNotEmpty()) {
                            _ticketScanState.value = buildConfirmState(products, body?.supermarket)
                        } else {
                            _ticketScanState.value = TicketScanState.Error(
                                "No se reconoció ningún producto en el ticket. Probá con fotos más nítidas."
                            )
                        }
                        return@launch
                    }

                    if (attempt == maxAttempts) {
                        _ticketScanState.value = TicketScanState.Error(
                            "No se pudo escanear el ticket (error del servidor). Intentá de nuevo en unos segundos."
                        )
                        return@launch
                    }
                    delay(attempt * 1500L)
                }
            } catch (e: Exception) {
                _ticketScanState.value = TicketScanState.Error("Error al escanear: ${e.message}")
            }
        }
    }

    // Las fotos de cámara salen a resolución completa (varios MB cada una) — subirlas crudas
    // hace que el body JSON supere el límite del backend y la llamada a Gemini falle en silencio
    // (cae al fallback de ML Kit, que es mucho peor). Las reescalamos a un ancho máximo razonable
    // para OCR y las recomprimimos a JPEG antes de mandarlas, corrigiendo además la rotación EXIF
    // (al recomprimir se pierden los metadatos, así que hay que rotar los píxeles a mano).
    // maxDimension generoso a propósito: el texto de un ticket térmico es chico y cualquier
    // downscale agresivo le come legibilidad al OCR. Esto solo actúa como freno para fotos
    // extremas (cámaras de 50+ MP); una foto de celular típica (3000-6000px de lado largo)
    // pasa prácticamente intacta. Esta misma versión comprimida es la que se persiste como
    // registro de la compra (no se guarda el original sin comprimir).
    private fun resizeImageForUpload(context: Context, uri: Uri, maxDimension: Int = 6000, quality: Int = 90): ByteArray? {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null

        val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        } else original

        val scale = maxDimension.toFloat() / maxOf(rotated.width, rotated.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true)
        } else rotated

        return java.io.ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }

    // Llama a /products/match-seed para los productos detectados y arma el estado de confirmación
    // con el resultado del matching (auto-vinculado o candidatos para elegir manualmente).
    private suspend fun buildConfirmState(products: List<ScannedProductDto>, supermarket: String?): TicketScanState {
        val matchResult = productRepository.matchSeed(products.map { it.name })
        val matches = (matchResult as? ApiResult.Success)?.data
        val items = products.mapIndexed { index, p ->
            val match = matches?.getOrNull(index)
            ScannedProductUi(
                product        = p,
                seedMatch      = match?.seedMatch,
                seedCandidates = match?.candidates ?: emptyList()
            )
        }
        return TicketScanState.Confirm(items, supermarket)
    }

    // Cambia o quita el vínculo de un producto a la seed (elegido a mano por el usuario).
    fun updateSeedLink(index: Int, seedProductName: String?) {
        val current = _ticketScanState.value
        if (current is TicketScanState.Confirm) {
            val updated = current.items.toMutableList()
            updated[index] = updated[index].copy(seedMatch = seedProductName)
            _ticketScanState.value = current.copy(items = updated)
        }
    }

    // Corrección manual de un producto detectado por la IA (nombre, precio o cantidad mal leídos).
    fun updateScannedProduct(index: Int, name: String, price: Double, quantity: Int) {
        val current = _ticketScanState.value
        if (current is TicketScanState.Confirm) {
            val updated = current.items.toMutableList()
            val item = updated[index]
            updated[index] = item.copy(product = item.product.copy(name = name, price = price, quantity = quantity))
            _ticketScanState.value = current.copy(items = updated)
        }
    }

    // Búsqueda libre en el catálogo para el buscador manual de vínculo.
    suspend fun searchSeedProducts(query: String): List<SeedSearchResultDto> {
        val result = productRepository.searchSeedProducts(query)
        return (result as? ApiResult.Success)?.data ?: emptyList()
    }

    // Confirmar e insertar los productos detectados en la compra, con su vínculo a la seed (si lo hay).
    fun confirmScannedProducts(purchaseId: Int, items: List<ScannedProductUi>) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Inserting
            try {
                items.forEach { item ->
                    val p = item.product
                    productRepository.createProduct(
                        purchaseId      = purchaseId,
                        code            = p.code,
                        name            = p.name,
                        description     = p.description,
                        price           = p.price,
                        quantity        = p.quantity,
                        category        = p.category,
                        seedProductName = item.seedMatch
                    )
                }
                _ticketScanState.value = TicketScanState.Done
                loadPurchase(purchaseId)
            } catch (e: Exception) {
                _ticketScanState.value = TicketScanState.Error("Error al guardar productos: ${e.message}")
            }
        }
    }
}
```

- [ ] **Step 2: Verificar que compila**

Run: `gradlew.bat :app:compileDebugKotlin`
Expected: FAIL — esperado en este punto: `PurchaseDetailScreen.kt` todavía llama a `viewModel.scanTicketFromUris(...)`, que ya no existe. Se corrige en la Task 8.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailViewModel.kt
git commit -m "feat: separar guardar fotos de ticket del escaneo con IA en el ViewModel"
```

(El build queda roto a propósito hasta la Task 8 — es un solo cambio lógico partido en dos commits porque el archivo del ViewModel ya es grande de por sí; si preferís un solo commit por las dudas de tener un commit que no compila en el historial, hacé las Tasks 7 y 8 juntas antes de commitear.)

---

### Task 8: Nuevos composables — `TicketPhotoStrip` y `TicketScanningOverlay`

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketPhotoStrip.kt`
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketScanningOverlay.kt`

- [ ] **Step 1: Crear el visor de fotos guardadas**

`app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketPhotoStrip.kt`:

```kotlin
// TicketPhotoStrip.kt — fila de miniaturas de las fotos del ticket ya guardadas como registro
// de la compra. Tocar una miniatura la abre en grande a pantalla completa.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.undef.superahorroturina.R
import com.undef.superahorroturina.data.local.db.TicketPhotoEntity

@Composable
fun TicketPhotoStrip(photos: List<TicketPhotoEntity>) {
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(photos) { index, photo ->
            AsyncImage(
                model              = photo.filePath,
                contentDescription = stringResource(R.string.ticket_photo_description, index + 1),
                contentScale       = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewerIndex = index }
            )
        }
    }

    viewerIndex?.let { index ->
        TicketPhotoViewerDialog(
            photos       = photos,
            initialIndex = index,
            onDismiss    = { viewerIndex = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketPhotoViewerDialog(
    photos: List<TicketPhotoEntity>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    var index by remember { mutableStateOf(initialIndex) }

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.ticket_photo_viewer_title, index + 1, photos.size)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model              = photos[index].filePath,
                    contentDescription = stringResource(R.string.ticket_photo_description, index + 1),
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }
    }
}
```

- [ ] **Step 2: Crear el overlay de progreso a pantalla completa**

`app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketScanningOverlay.kt`:

```kotlin
// TicketScanningOverlay.kt — pantalla completa bloqueante mientras la IA procesa el ticket.
// Es indeterminada (no hay % real: es un solo llamado a Gemini sin pasos intermedios para medir),
// pero a pantalla completa con texto explícito para que sea imposible no notar que está trabajando.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R

@Composable
fun TicketScanningOverlay() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Text(
                text  = stringResource(R.string.ticket_scanning_overlay_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
```

- [ ] **Step 3: Verificar que compila**

Run: `gradlew.bat :app:compileDebugKotlin`
Expected: sigue en el mismo estado de la Task 7 (`PurchaseDetailScreen.kt` todavía no usa nada de esto) — no debería haber errores nuevos provenientes de estos dos archivos nuevos. Si aparece un error en `PurchaseDetailScreen.kt` por `scanTicketFromUris`, es el esperado de la Task 7, se resuelve en la Task 9.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketPhotoStrip.kt app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketScanningOverlay.kt
git commit -m "feat: composables TicketPhotoStrip y TicketScanningOverlay"
```

---

### Task 9: Conectar todo en `PurchaseDetailScreen` y `TicketPhotosPreviewScreen`

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailScreen.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketPhotosPreviewScreen.kt`

- [ ] **Step 1: Cambiar el botón de confirmar en `TicketPhotosPreviewScreen`**

En `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketPhotosPreviewScreen.kt`, reemplazar la firma de la función y el botón de confirmar (líneas 32-72):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPhotosPreviewScreen(
    photos: List<Uri>,
    onRemove: (Int) -> Unit,
    onAddMore: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ticket_photos_title, photos.size)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = onAddMore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_add_more_photos))
                    }
                    Button(
                        onClick  = onSave,
                        enabled  = photos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_save_photos, photos.size))
                    }
                }
            }
        }
    ) { padding ->
```

(El resto del archivo, desde `if (photos.isEmpty()) {` en adelante, queda igual — no se toca.)

- [ ] **Step 2: Cambiar el flujo en `PurchaseDetailScreen`**

En `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailScreen.kt`:

Agregar este import junto a los demás (después de `import com.undef.superahorroturina.ui.components.*`):

```kotlin
import com.undef.superahorroturina.data.local.db.TicketPhotoEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

(`collectAsStateWithLifecycle` ya está importado en el archivo — no duplicar si ya aparece.)

Reemplazar el bloque que lee `uiState`/`ticketState` (líneas 62-63) para agregar la colección de fotos guardadas:

```kotlin
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val ticketState   by viewModel.ticketScanState.collectAsStateWithLifecycle()
    val ticketPhotos  by remember(purchaseId) { viewModel.ticketPhotosFlow(purchaseId) }
        .collectAsStateWithLifecycle(initialValue = emptyList<TicketPhotoEntity>())
    val context        = LocalContext.current
    val isDark         = isSystemInDarkTheme()
```

Reemplazar el bloque de `TicketScanState.Confirm` (líneas 75-88) para que el overlay de escaneo se muestre también durante `Scanning`/`Inserting`, antes que cualquier otra cosa:

```kotlin
    if (ticketState is TicketScanState.Scanning || ticketState is TicketScanState.Inserting) {
        TicketScanningOverlay()
        return
    }

    if (ticketState is TicketScanState.Confirm) {
        val confirmState = ticketState as TicketScanState.Confirm
        TicketConfirmScreen(
            products     = confirmState.items,
            supermarket  = confirmState.supermarket,
            moneyFormat  = moneyFormat,
            onSearchSeed = { query -> viewModel.searchSeedProducts(query) },
            onLinkChange = { index, name -> viewModel.updateSeedLink(index, name) },
            onEditProduct = { index, name, price, quantity -> viewModel.updateScannedProduct(index, name, price, quantity) },
            onConfirm    = { viewModel.confirmScannedProducts(purchaseId, confirmState.items) },
            onCancel     = { viewModel.resetTicketScan() }
        )
        return
    }
```

Reemplazar el bloque `if (stagedPhotos.isNotEmpty())` (líneas 126-141) para que confirmar guarde las fotos en vez de escanear:

```kotlin
    if (stagedPhotos.isNotEmpty()) {
        TicketPhotosPreviewScreen(
            photos   = stagedPhotos,
            onRemove = { index -> stagedPhotos = stagedPhotos.toMutableList().also { it.removeAt(index) } },
            onAddMore = { showPhotoSourceDialog = true },
            onSave = {
                val photosToSave = stagedPhotos
                stagedPhotos = emptyList()
                viewModel.savePhotosForPurchase(context, photosToSave, purchaseId)
            },
            onCancel = { stagedPhotos = emptyList() }
        )
        return
    }
```

Reemplazar el `when (val state = ticketState)` de errores (líneas 144-159) — ya no hace falta filtrar `Scanning`/`Inserting` ahí porque ahora se resuelven antes con el `return` del overlay, así que el bloque queda igual, pero confirmá que sigue siendo:

```kotlin
    when (val state = ticketState) {
        is TicketScanState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetTicketScan() },
                title = { Text(stringResource(R.string.dialog_scan_error_title)) },
                text  = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetTicketScan() }) { Text(stringResource(R.string.action_close)) }
                }
            )
        }
        is TicketScanState.Done -> {
            LaunchedEffect(Unit) { viewModel.resetTicketScan() }
        }
        else -> Unit
    }
```

Reemplazar la llamada a `TicketAttachCard` dentro del `LazyColumn` (líneas 284-290):

```kotlin
                    item {
                        TicketAttachCard(
                            photos        = ticketPhotos,
                            isDark        = isDark,
                            onAttachClick = { showPhotoSourceDialog = true },
                            onManualClick = { onNavigateToAddProduct(purchase.id) },
                            onAiClick     = { viewModel.scanTicketFromSavedPhotos(purchase.id) }
                        )
                    }
```

Reemplazar el composable `TicketAttachCard` completo (líneas 441-501):

```kotlin
@Composable
private fun TicketAttachCard(
    photos: List<TicketPhotoEntity>,
    isDark: Boolean,
    onAttachClick: () -> Unit,
    onManualClick: () -> Unit,
    onAiClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .coloredShadow(
                color        = MaterialTheme.colorScheme.secondary,
                borderRadius = 16.dp,
                blurRadius   = 8.dp,
                offsetY      = 2.dp
            )
            .glowBorder(cornerRadius = 16.dp, isDark = isDark),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (photos.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Receipt, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.purchase_ticket),
                                style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.purchase_ticket_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FilledTonalButton(
                        onClick = onAttachClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_attach),
                            maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Text(stringResource(R.string.ticket_photos_saved_title),
                    style = MaterialTheme.typography.titleSmall)
                TicketPhotoStrip(photos = photos)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = onManualClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ticket_choice_manual),
                            maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick  = onAiClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ticket_choice_ai),
                            maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verificar que compila**

Run: `gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verificar que el build completo (con lint de recursos) pasa**

Run: `gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailScreen.kt app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketPhotosPreviewScreen.kt
git commit -m "feat: conectar foto-primero + eleccion manual/IA en PurchaseDetailScreen"
```

---

### Task 10: QA manual en dispositivo/emulador (tarea humana)

**Files:** ninguno (verificación manual).

- [ ] **Step 1:** Instalar y abrir la app en un dispositivo/emulador (`gradlew.bat :app:installDebug`).
- [ ] **Step 2:** Crear o abrir una compra. Tocar "Adjuntar" → sacar una sola foto → confirmar. Verificar que aparece la fila de miniaturas y los dos botones ("Cargar manualmente" / "Ayudame con IA"), y que el botón "Adjuntar" desaparece.
- [ ] **Step 3:** Repetir con una foto elegida de galería, y con varias fotos (ticket largo) en la misma tanda.
- [ ] **Step 4:** Tocar la miniatura de una foto guardada y verificar que abre el visor a pantalla completa, y que volver atrás no rompe nada.
- [ ] **Step 5:** Tocar "Ayudame con IA" y verificar que aparece el overlay a pantalla completa con la barra indeterminada y el texto, que bloquea la interacción, y que al terminar pasa a la pantalla de confirmación de productos de siempre.
- [ ] **Step 6:** Desde la misma compra (ya con productos confirmados o no), volver a tocar "Cargar manualmente" y verificar que lleva al formulario manual de siempre — confirmando que se puede cambiar de método en cualquier momento.
- [ ] **Step 7:** Forzar un error de red (modo avión) y tocar "Ayudame con IA": verificar que se cierra el overlay y aparece el diálogo de error existente, y que las fotos guardadas siguen ahí después de cerrar el diálogo.
- [ ] **Step 8:** Borrar la compra y verificar (con un explorador de archivos root/adb, `adb shell run-as com.undef.superahorroturina ls files/ticket_photos/`) que la carpeta de esa compra ya no existe.

---

## Resumen de cobertura del spec

- Persistencia de la(s) foto(s) como registro → Tasks 2-5.
- Reemplazo de "Adjuntar ticket" por el flujo nuevo, FAB intacto → Task 9.
- Elección manual/IA reversible en cualquier momento → Task 9 (botones siempre visibles junto a la fila de miniaturas, no solo tras sacar la foto).
- Barra de progreso a pantalla completa durante el escaneo → Tasks 8-9.
- Limpieza de archivos al borrar la compra → Task 5.
- Fotos comprimidas, no el original → Task 7 (`savePhotosForPurchase` reusa `resizeImageForUpload`).
