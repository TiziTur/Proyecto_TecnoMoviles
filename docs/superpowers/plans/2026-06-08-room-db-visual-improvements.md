# Room DB + Visual Improvements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar Room DB con cache-first y aplicar diseño premium (dot pattern + glassmorphism) a las 4 pantallas de formulario que hoy tienen fondo blanco plano.

**Architecture:** Las entidades Room replican la API; los repositorios escriben en Room al recibir respuesta exitosa y exponen un `Flow<List<Purchase>>` que los ViewModels colectan de forma reactiva. La UI recibe datos instantáneamente desde Room mientras la API refresca en background.

**Tech Stack:** Room 2.x (kapt), Hilt, Kotlin Coroutines/Flow, Jetpack Compose, Material3, custom Compose modifiers (`dotPatternBackground`, `coloredShadow`, `glowBorder` de KlarityDesign.kt)

---

## Mapa de archivos

**Crear:**
- `app/src/main/java/com/undef/superahorroturina/data/local/db/PurchaseEntity.kt`
- `app/src/main/java/com/undef/superahorroturina/data/local/db/ProductEntity.kt`
- `app/src/main/java/com/undef/superahorroturina/data/local/db/PurchaseDao.kt`
- `app/src/main/java/com/undef/superahorroturina/data/local/db/ProductDao.kt`
- `app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt`
- `app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt`

**Reescribir completamente:**
- `data/repository/PurchaseRepository.kt` — añade Flow + Room writes
- `data/repository/ProductRepository.kt` — añade ProductDao
- `ui/screens/home/HomeViewModel.kt` — colecta Flow de Room
- `ui/screens/history/HistoryViewModel.kt` — colecta Flow de Room
- `ui/screens/purchase/NewPurchaseScreen.kt` — visual upgrade
- `ui/screens/product/ProductFormScreen.kt` — visual upgrade
- `ui/screens/profile/ProfileScreen.kt` — visual upgrade
- `ui/screens/settings/SettingsScreen.kt` — visual upgrade

**Verificar y actualizar si necesario:**
- `ui/screens/stats/StatsViewModel.kt` — puede usar getPurchases(); migrar a refreshPurchases()

---

### Task 1: Entidades Room

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/data/local/db/PurchaseEntity.kt`
- Create: `app/src/main/java/com/undef/superahorroturina/data/local/db/ProductEntity.kt`

- [ ] **Step 1: Crear PurchaseEntity.kt**

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val id: Int,
    val purchaseDate: String,
    val purchaseTime: String,
    val supermarket: String,
    val total: Double,
    val productCount: Int
)
```

- [ ] **Step 2: Crear ProductEntity.kt**

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(
        entity = PurchaseEntity::class,
        parentColumns = ["id"],
        childColumns = ["purchaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("purchaseId")]
)
data class ProductEntity(
    @PrimaryKey val id: Int,
    val purchaseId: Int,
    val code: String,
    val name: String,
    val description: String,
    val price: Double,
    val quantity: Int
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/local/db/PurchaseEntity.kt
git add app/src/main/java/com/undef/superahorroturina/data/local/db/ProductEntity.kt
git commit -m "feat: add Room entities PurchaseEntity and ProductEntity"
```

---

### Task 2: DAOs

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/data/local/db/PurchaseDao.kt`
- Create: `app/src/main/java/com/undef/superahorroturina/data/local/db/ProductDao.kt`

- [ ] **Step 1: Crear PurchaseDao.kt**

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(purchases: List<PurchaseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(purchase: PurchaseEntity)

    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC, purchaseTime DESC")
    fun getAll(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getById(id: Int): PurchaseEntity?

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM purchases")
    suspend fun deleteAll()
}
```

- [ ] **Step 2: Crear ProductDao.kt**

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Query("SELECT * FROM products WHERE purchaseId = :purchaseId")
    fun getByPurchaseId(purchaseId: Int): Flow<List<ProductEntity>>

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM products WHERE purchaseId = :purchaseId")
    suspend fun deleteByPurchaseId(purchaseId: Int)
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/local/db/PurchaseDao.kt
git add app/src/main/java/com/undef/superahorroturina/data/local/db/ProductDao.kt
git commit -m "feat: add Room DAOs for purchases and products"
```

---

### Task 3: AppDatabase + DatabaseModule (Hilt)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt`
- Create: `app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt`

- [ ] **Step 1: Crear AppDatabase.kt**

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PurchaseEntity::class, ProductEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
    abstract fun productDao(): ProductDao
}
```

- [ ] **Step 2: Crear DatabaseModule.kt**

```kotlin
package com.undef.superahorroturina.di

import android.content.Context
import androidx.room.Room
import com.undef.superahorroturina.data.local.db.AppDatabase
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.PurchaseDao
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePurchaseDao(db: AppDatabase): PurchaseDao = db.purchaseDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
}
```

- [ ] **Step 3: Build parcial — verificar kapt**

Ejecutar en la raíz del proyecto:
```bash
./gradlew :app:kaptDebugKotlin
```
Expected: BUILD SUCCESSFUL. Si hay errores kapt, revisar que las entidades tengan `@PrimaryKey` y que `AppDatabase` extienda `RoomDatabase`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt
git add app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt
git commit -m "feat: add AppDatabase and Hilt DatabaseModule"
```

---

### Task 4: PurchaseRepository — cache-first

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/repository/PurchaseRepository.kt`

Reemplazar el archivo completo con la siguiente implementación:

- [ ] **Step 1: Reescribir PurchaseRepository.kt**

```kotlin
package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.ProductEntity
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.PurchaseDao
import com.undef.superahorroturina.data.local.db.PurchaseEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.CreatePurchaseRequest
import com.undef.superahorroturina.data.network.dto.PurchaseDto
import com.undef.superahorroturina.data.network.dto.UpdatePurchaseRequest
import com.undef.superahorroturina.model.Product
import com.undef.superahorroturina.model.Purchase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao
) {
    // Reactive stream from Room — ViewModels collect this
    fun getPurchasesFlow(): Flow<List<Purchase>> =
        purchaseDao.getAll().map { entities -> entities.map { it.toDomain() } }

    // Fetch from API and save to Room; Flow auto-updates collectors
    suspend fun refreshPurchases(): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getPurchases(token)
        if (response.isSuccessful) {
            val dtos = response.body()!!
            purchaseDao.upsertAll(dtos.map { it.toEntity() })
            dtos.forEach { dto ->
                if (dto.products.isNotEmpty()) {
                    productDao.upsertAll(dto.products.map { p ->
                        ProductEntity(p.id, dto.id, p.code, p.name, p.description, p.price, p.quantity)
                    })
                }
            }
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al cargar compras: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun getPurchase(id: Int): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getPurchase(token, id)
        if (response.isSuccessful) {
            val dto = response.body()!!
            purchaseDao.upsert(dto.toEntity())
            productDao.upsertAll(dto.products.map { p ->
                ProductEntity(p.id, id, p.code, p.name, p.description, p.price, p.quantity)
            })
            ApiResult.Success(dto.toDomain())
        } else {
            ApiResult.Error("Compra no encontrada")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun createPurchase(supermarket: String, date: String, time: String): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.createPurchase(token, CreatePurchaseRequest(date, time, supermarket))
        if (response.isSuccessful) {
            val dto = response.body()!!
            purchaseDao.upsert(dto.toEntity())
            ApiResult.Success(dto.toDomain())
        } else {
            ApiResult.Error("Error al crear compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun updatePurchase(id: Int, supermarket: String, date: String, time: String): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.updatePurchase(token, id, UpdatePurchaseRequest(date, time, supermarket))
        if (response.isSuccessful) {
            val dto = response.body()!!
            purchaseDao.upsert(dto.toEntity())
            ApiResult.Success(dto.toDomain())
        } else {
            ApiResult.Error("Error al actualizar compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun deletePurchase(id: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.deletePurchase(token, id)
        if (response.isSuccessful) {
            purchaseDao.delete(id) // CASCADE borra los productos de ese purchase
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al eliminar compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    // ── Conversiones ──────────────────────────────────────────────

    private fun PurchaseDto.toEntity() = PurchaseEntity(
        id           = id,
        purchaseDate = purchaseDate,
        purchaseTime = purchaseTime,
        supermarket  = supermarket,
        total        = total,
        productCount = maxOf(productCount, products.size)
    )

    private fun PurchaseEntity.toDomain() = Purchase(
        id           = id,
        date         = runCatching { LocalDate.parse(purchaseDate) }.getOrElse { LocalDate.now() },
        time         = runCatching { LocalTime.parse(purchaseTime.take(5)) }.getOrElse { LocalTime.MIDNIGHT },
        supermarket  = supermarket,
        total        = total,
        productCount = productCount,
        products     = emptyList()
    )

    private fun PurchaseDto.toDomain(): Purchase {
        val mappedProducts = products.map { p ->
            Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
        }
        val resolvedCount = maxOf(productCount, mappedProducts.size)
        return Purchase(
            id           = id,
            date         = runCatching { LocalDate.parse(purchaseDate) }.getOrElse { LocalDate.now() },
            time         = runCatching { LocalTime.parse(purchaseTime.take(5)) }.getOrElse { LocalTime.MIDNIGHT },
            supermarket  = supermarket,
            total        = total,
            productCount = resolvedCount,
            products     = mappedProducts
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/repository/PurchaseRepository.kt
git commit -m "feat: PurchaseRepository cache-first with Room + getPurchasesFlow()"
```

---

### Task 5: ProductRepository — añadir Room

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/repository/ProductRepository.kt`

- [ ] **Step 1: Reescribir ProductRepository.kt**

```kotlin
package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.ProductEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.CreateProductRequest
import com.undef.superahorroturina.data.network.dto.UpdateProductRequest
import com.undef.superahorroturina.model.Product
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val productDao: ProductDao
) {
    suspend fun getProducts(purchaseId: Int): ApiResult<List<Product>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getProducts(token, purchaseId)
        if (response.isSuccessful) {
            val products = response.body()!!.map {
                Product(it.id, it.code, it.name, it.description, it.price, it.quantity)
            }
            productDao.upsertAll(products.map { p ->
                ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity)
            })
            ApiResult.Success(products)
        } else {
            ApiResult.Error("Error al cargar productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun createProduct(
        purchaseId: Int, code: String, name: String,
        description: String, price: Double, quantity: Int
    ): ApiResult<Product> = runCatching {
        val token = session.bearerToken.first()
        val response = api.createProduct(
            token, purchaseId,
            CreateProductRequest(code, name, description, price, quantity)
        )
        if (response.isSuccessful) {
            val p = response.body()!!
            val product = Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
            productDao.upsert(ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity))
            ApiResult.Success(product)
        } else {
            ApiResult.Error("Error al crear producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun updateProduct(
        purchaseId: Int, productId: Int, code: String, name: String,
        description: String, price: Double, quantity: Int
    ): ApiResult<Product> = runCatching {
        val token = session.bearerToken.first()
        val response = api.updateProduct(
            token, purchaseId, productId,
            UpdateProductRequest(code, name, description, price, quantity)
        )
        if (response.isSuccessful) {
            val p = response.body()!!
            val product = Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
            productDao.upsert(ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity))
            ApiResult.Success(product)
        } else {
            ApiResult.Error("Error al actualizar producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun deleteProduct(purchaseId: Int, productId: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.deleteProduct(token, purchaseId, productId)
        if (response.isSuccessful) {
            productDao.delete(productId)
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al eliminar producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/repository/ProductRepository.kt
git commit -m "feat: ProductRepository writes to Room on every successful API call"
```

---

### Task 6: HomeViewModel — Flow desde Room

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/home/HomeViewModel.kt`

- [ ] **Step 1: Reescribir HomeViewModel.kt**

```kotlin
package com.undef.superahorroturina.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.ui.state.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val sessionDataStore: SessionDataStore,
    private val themeDataStore: ThemeDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Colectar Flow reactivo de Room — UI se actualiza automáticamente
        viewModelScope.launch {
            purchaseRepository.getPurchasesFlow().collect { purchases ->
                val now          = java.time.LocalDate.now()
                val session      = sessionDataStore.session.first()
                val monthlyLimit = themeDataStore.monthlyLimit.first()
                val thisMonth    = purchases
                    .filter { it.date.monthValue == now.monthValue && it.date.year == now.year }
                    .sumOf { it.total }
                _uiState.value = _uiState.value.copy(
                    isLoading        = false,
                    isRefreshing     = false,
                    userName         = session.firstName,
                    totalThisMonth   = thisMonth,
                    monthlyLimit     = monthlyLimit,
                    recentPurchases  = purchases.sortedByDescending { it.date }.take(5),
                    purchaseCount    = purchases.size,
                    supermarketCount = purchases.map { it.supermarket }.distinct().size
                )
            }
        }
        // Observar límite mensual en tiempo real
        viewModelScope.launch {
            themeDataStore.monthlyLimit.collect { limit ->
                _uiState.value = _uiState.value.copy(monthlyLimit = limit)
            }
        }
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            purchaseRepository.refreshPurchases()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, isLoading = false)
            purchaseRepository.refreshPurchases()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/home/HomeViewModel.kt
git commit -m "feat: HomeViewModel reactive via Room Flow"
```

---

### Task 7: HistoryViewModel — Flow desde Room

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/history/HistoryViewModel.kt`

- [ ] **Step 1: Reescribir HistoryViewModel.kt**

```kotlin
package com.undef.superahorroturina.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val purchases: List<Purchase> = emptyList(),
    val filteredPurchases: List<Purchase> = emptyList(),
    val error: String = ""
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val searchQuery    = MutableStateFlow("")
    val selectedFilter = MutableStateFlow("Todos")

    init {
        // Colectar Flow reactivo de Room
        viewModelScope.launch {
            purchaseRepository.getPurchasesFlow().collect { purchases ->
                val sorted = purchases.sortedByDescending { it.date }
                _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    isRefreshing = false,
                    purchases    = sorted,
                    error        = ""
                )
            }
        }
        // Filtrado reactivo con debounce
        combine(
            _uiState.map { it.purchases },
            searchQuery.debounce(300),
            selectedFilter
        ) { purchases, query, filter ->
            purchases.filter { purchase ->
                val matchesSearch = query.isBlank() ||
                    purchase.supermarket.contains(query, ignoreCase = true)
                val matchesFilter = filter == "Todos" || purchase.supermarket == filter
                matchesSearch && matchesFilter
            }
        }
            .onEach { filtered ->
                _uiState.value = _uiState.value.copy(filteredPurchases = filtered)
            }
            .launchIn(viewModelScope)

        loadPurchases()
    }

    fun loadPurchases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            purchaseRepository.refreshPurchases()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            purchaseRepository.refreshPurchases()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun deletePurchase(purchaseId: Int) {
        viewModelScope.launch {
            purchaseRepository.deletePurchase(purchaseId)
            // No necesita recargar: el Flow de Room se actualiza solo al borrar
        }
    }
}
```

- [ ] **Step 2: Verificar StatsViewModel**

Leer `ui/screens/stats/StatsViewModel.kt`. Si contiene una llamada a `purchaseRepository.getPurchases()` (el método viejo que ya no existe), reemplazarla por:

```kotlin
// En el init o en la función de carga:
viewModelScope.launch {
    purchaseRepository.refreshPurchases()
    purchaseRepository.getPurchasesFlow().first().let { purchases ->
        // procesar purchases para stats
    }
}
```

Si StatsViewModel no usa PurchaseRepository, no hacer cambios.

- [ ] **Step 3: Build parcial**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL. Si hay `Unresolved reference: getPurchases`, buscar todos los archivos que aún llamen a ese método y actualizar.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/history/HistoryViewModel.kt
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsViewModel.kt  # solo si fue modificado
git commit -m "feat: HistoryViewModel reactive via Room Flow, remove getPurchases() callers"
```

---

### Task 8: Visual — NewPurchaseScreen

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/NewPurchaseScreen.kt`

Añade dot pattern de fondo y glassmorphism card para el formulario.

- [ ] **Step 1: Reescribir NewPurchaseScreen.kt**

```kotlin
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.KlarityButton
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.dotPatternBackground
import com.undef.superahorroturina.ui.components.glowBorder
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPurchaseScreen(
    purchaseId: Int?,
    onNavigateBack: () -> Unit,
    onNavigateToPurchaseDetail: ((Int) -> Unit)? = null,
    viewModel: NewPurchaseViewModel = hiltViewModel()
) {
    LaunchedEffect(purchaseId) { viewModel.loadPurchase(purchaseId) }

    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing   = purchaseId != null
    val isDark      = isSystemInDarkTheme()
    val moneyFormat = NumberFormat.getNumberInstance(Locale("es", "AR"))
    val title       = if (isEditing) stringResource(R.string.purchase_edit_title)
                      else           stringResource(R.string.purchase_new_title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = title, showBack = true, onBack = onNavigateBack) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dotPatternBackground(
                    dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                    dotRadius = 1.2f,
                    spacing   = 22f
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Form card glassmorphism ──────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.primary,
                            borderRadius = 20.dp,
                            blurRadius   = 16.dp,
                            offsetY      = 4.dp
                        )
                        .glowBorder(cornerRadius = 20.dp, isDark = isDark),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded        = uiState.dropdownExpanded,
                            onExpandedChange = { viewModel.onDropdownExpandedChange(!uiState.dropdownExpanded) }
                        ) {
                            OutlinedTextField(
                                value          = uiState.supermarket,
                                onValueChange  = {},
                                readOnly       = true,
                                label          = { Text(stringResource(R.string.field_supermarket)) },
                                leadingIcon    = { Icon(Icons.Default.Store, contentDescription = null) },
                                trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.dropdownExpanded) },
                                modifier       = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                shape = MaterialTheme.shapes.medium
                            )
                            ExposedDropdownMenu(
                                expanded        = uiState.dropdownExpanded,
                                onDismissRequest = { viewModel.onDropdownExpandedChange(false) }
                            ) {
                                uiState.supermarketList.forEach { market ->
                                    DropdownMenuItem(
                                        text    = { Text(market) },
                                        onClick = {
                                            viewModel.onSupermarketChange(market)
                                            viewModel.onDropdownExpandedChange(false)
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value          = uiState.date,
                                onValueChange  = { viewModel.onDateChange(it) },
                                label          = { Text(stringResource(R.string.field_date)) },
                                leadingIcon    = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                                placeholder    = { Text("dd/MM/yyyy") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier       = Modifier.weight(1f),
                                singleLine     = true,
                                shape          = MaterialTheme.shapes.medium
                            )
                            OutlinedTextField(
                                value          = uiState.time,
                                onValueChange  = { viewModel.onTimeChange(it) },
                                label          = { Text(stringResource(R.string.field_time)) },
                                leadingIcon    = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                                placeholder    = { Text("HH:mm") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier       = Modifier.weight(1f),
                                singleLine     = true,
                                shape          = MaterialTheme.shapes.medium
                            )
                        }
                    }
                }

                // ── Total card con gradiente ─────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.primary,
                            borderRadius = 16.dp,
                            blurRadius   = 14.dp,
                            offsetY      = 3.dp
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.purchase_total),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                stringResource(R.string.purchase_total_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            "$ ${moneyFormat.format(uiState.total)}",
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }

                if (uiState.saveError.isNotBlank()) {
                    Text(
                        uiState.saveError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                KlarityButton(
                    text    = stringResource(R.string.action_save),
                    onClick = {
                        viewModel.onSave { newId ->
                            if (newId != null && onNavigateToPurchaseDetail != null)
                                onNavigateToPurchaseDetail(newId)
                            else
                                onNavigateBack()
                        }
                    },
                    loading  = uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/NewPurchaseScreen.kt
git commit -m "feat: NewPurchaseScreen visual upgrade - dot pattern + glassmorphism card"
```

---

### Task 9: Visual — ProductFormScreen

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/product/ProductFormScreen.kt`

- [ ] **Step 1: Reescribir ProductFormScreen.kt**

```kotlin
package com.undef.superahorroturina.ui.screens.product

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.KlarityButton
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.dotPatternBackground
import com.undef.superahorroturina.ui.components.glowBorder
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductFormScreen(
    purchaseId: Int,
    productId: Int?,
    onNavigateBack: () -> Unit,
    viewModel: ProductFormViewModel = hiltViewModel()
) {
    LaunchedEffect(purchaseId, productId) { viewModel.loadProduct(purchaseId, productId) }

    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing   = productId != null
    val isDark      = isSystemInDarkTheme()
    val moneyFormat = NumberFormat.getNumberInstance(Locale("es", "AR"))
    val title       = if (isEditing) stringResource(R.string.product_edit_title)
                      else           stringResource(R.string.product_new_title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = title, showBack = true, onBack = onNavigateBack) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dotPatternBackground(
                    dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                    dotRadius = 1.2f,
                    spacing   = 22f
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Form card glassmorphism ──────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.primary,
                            borderRadius = 20.dp,
                            blurRadius   = 16.dp,
                            offsetY      = 4.dp
                        )
                        .glowBorder(cornerRadius = 20.dp, isDark = isDark),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Código de barras
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value          = uiState.code,
                                onValueChange  = { viewModel.onCodeChange(it) },
                                label          = { Text(stringResource(R.string.field_code)) },
                                leadingIcon    = { Icon(Icons.Default.QrCode, contentDescription = null) },
                                placeholder    = { Text("7790895000084") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier       = Modifier.weight(1f),
                                singleLine     = true,
                                supportingText = {
                                    Text("EAN / código de barras",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            )
                            FilledTonalIconButton(
                                onClick  = { /* TODO: Intent cámara para escanear EAN */ },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt,
                                    contentDescription = "Escanear código de barras",
                                    modifier = Modifier.size(24.dp))
                            }
                        }

                        OutlinedTextField(
                            value          = uiState.name,
                            onValueChange  = { viewModel.onNameChange(it) },
                            label          = { Text(stringResource(R.string.field_name)) },
                            leadingIcon    = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true
                        )

                        OutlinedTextField(
                            value          = uiState.description,
                            onValueChange  = { viewModel.onDescriptionChange(it) },
                            label          = { Text(stringResource(R.string.field_description)) },
                            leadingIcon    = { Icon(Icons.Default.Description, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier       = Modifier.fillMaxWidth(),
                            minLines       = 2,
                            maxLines       = 3
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value          = uiState.price,
                                onValueChange  = { viewModel.onPriceChange(it) },
                                label          = { Text(stringResource(R.string.field_price)) },
                                leadingIcon    = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError        = uiState.priceError,
                                supportingText = if (uiState.priceError) {
                                    { Text(stringResource(R.string.error_price)) }
                                } else null,
                                modifier   = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value          = uiState.quantity,
                                onValueChange  = { viewModel.onQuantityChange(it) },
                                label          = { Text(stringResource(R.string.field_quantity)) },
                                leadingIcon    = { Icon(Icons.Default.Numbers, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError        = uiState.quantityError,
                                modifier       = Modifier.weight(1f),
                                singleLine     = true
                            )
                        }
                    }
                }

                // ── Subtotal card con gradiente ──────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.secondary,
                            borderRadius = 16.dp,
                            blurRadius   = 12.dp,
                            offsetY      = 3.dp
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.product_subtotal),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            "$ ${moneyFormat.format(uiState.subtotal)}",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }

                if (uiState.saveError.isNotBlank()) {
                    Text(
                        uiState.saveError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                KlarityButton(
                    text     = stringResource(R.string.action_save),
                    onClick  = { viewModel.onSave(onNavigateBack) },
                    loading  = uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/product/ProductFormScreen.kt
git commit -m "feat: ProductFormScreen visual upgrade - dot pattern + glassmorphism card"
```

---

### Task 10: Visual — ProfileScreen

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/profile/ProfileScreen.kt`

- [ ] **Step 1: Reescribir ProfileScreen.kt**

```kotlin
package com.undef.superahorroturina.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.GradientDivider
import com.undef.superahorroturina.ui.components.KlarityButton
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.dotPatternBackground
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark   = isSystemInDarkTheme()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title    = stringResource(R.string.screen_profile),
                showBack = true,
                onBack   = onNavigateBack,
                actions  = {
                    IconButton(onClick = { viewModel.onToggleEditing() }) {
                        Icon(
                            imageVector = if (uiState.isEditing) Icons.Default.Check
                                          else Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dotPatternBackground(
                    dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                    dotRadius = 1.2f,
                    spacing   = 22f
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // ── Hero card con avatar ─────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.primary,
                            borderRadius = 24.dp,
                            blurRadius   = 20.dp,
                            offsetY      = 6.dp
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                ) {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val initials = buildString {
                            if (uiState.firstName.isNotEmpty()) append(uiState.firstName.first().uppercaseChar())
                            if (uiState.lastName.isNotEmpty())  append(uiState.lastName.first().uppercaseChar())
                        }
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = initials,
                                style      = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White
                            )
                        }
                        Text(
                            text       = "${uiState.firstName} ${uiState.lastName}",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                        Text(
                            text  = uiState.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        if (uiState.isEditing) {
                            TextButton(
                                onClick = { /* TODO: intent galería */ },
                                colors  = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.85f))
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.profile_change_photo))
                            }
                        }
                    }
                }

                // ── Campos editables en glass card ───────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.secondary,
                            borderRadius = 20.dp,
                            blurRadius   = 12.dp,
                            offsetY      = 3.dp
                        )
                        .glowBorder(cornerRadius = 20.dp, isDark = isDark),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        GradientDivider(color = MaterialTheme.colorScheme.primary)

                        OutlinedTextField(
                            value          = uiState.firstName,
                            onValueChange  = { viewModel.onFirstNameChange(it) },
                            label          = { Text(stringResource(R.string.field_first_name)) },
                            leadingIcon    = { Icon(Icons.Default.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            enabled        = uiState.isEditing,
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true,
                            shape          = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value          = uiState.lastName,
                            onValueChange  = { viewModel.onLastNameChange(it) },
                            label          = { Text(stringResource(R.string.field_last_name)) },
                            leadingIcon    = { Icon(Icons.Default.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            enabled        = uiState.isEditing,
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true,
                            shape          = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value          = uiState.email,
                            onValueChange  = { viewModel.onEmailChange(it) },
                            label          = { Text(stringResource(R.string.field_email)) },
                            leadingIcon    = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled        = uiState.isEditing,
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true,
                            shape          = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value          = uiState.phone,
                            onValueChange  = { viewModel.onPhoneChange(it) },
                            label          = { Text(stringResource(R.string.field_phone)) },
                            leadingIcon    = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            enabled        = uiState.isEditing,
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true,
                            shape          = MaterialTheme.shapes.medium
                        )

                        if (uiState.isEditing) {
                            KlarityButton(
                                text     = stringResource(R.string.action_save),
                                onClick  = { viewModel.onSave() },
                                loading  = uiState.isSaving,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/profile/ProfileScreen.kt
git commit -m "feat: ProfileScreen visual upgrade - hero card + glassmorphism fields"
```

---

### Task 11: Visual — SettingsScreen

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsScreen.kt`

- [ ] **Step 1: Reescribir SettingsScreen.kt**

```kotlin
package com.undef.superahorroturina.ui.screens.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.dotPatternBackground
import com.undef.superahorroturina.ui.components.glowBorder
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark      = isSystemInDarkTheme()
    val languages   = listOf("Español", "English")
    val sortOptions = listOf(
        stringResource(R.string.settings_sort_newest),
        stringResource(R.string.settings_sort_oldest),
        stringResource(R.string.settings_sort_highest)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title    = stringResource(R.string.screen_settings),
                showBack = true,
                onBack   = onNavigateBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dotPatternBackground(
                    dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                    dotRadius = 1.2f,
                    spacing   = 22f
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // ── Apariencia ────────────────────────────────────
                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_appearance))

                    SettingsToggleItem(
                        icon            = Icons.Default.DarkMode,
                        title           = stringResource(R.string.settings_dark_mode),
                        subtitle        = stringResource(R.string.settings_dark_mode_desc),
                        checked         = uiState.darkMode,
                        onCheckedChange = { viewModel.onDarkModeChange(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSelectorItem(
                        icon     = Icons.Default.Language,
                        title    = stringResource(R.string.settings_language),
                        subtitle = uiState.language,
                        onClick  = { viewModel.onLanguageExpandedChange(true) }
                    )

                    DropdownMenu(
                        expanded        = uiState.languageExpanded,
                        onDismissRequest = { viewModel.onLanguageExpandedChange(false) }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text    = { Text(lang) },
                                onClick = { viewModel.onLanguageChange(lang) },
                                leadingIcon = {
                                    if (uiState.language == lang)
                                        Icon(Icons.Default.Check, contentDescription = null)
                                }
                            )
                        }
                    }
                }

                // ── Notificaciones ────────────────────────────────
                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_notifications))

                    SettingsToggleItem(
                        icon            = Icons.Default.Notifications,
                        title           = stringResource(R.string.settings_notifications_label),
                        subtitle        = stringResource(R.string.settings_notifications_desc),
                        checked         = uiState.notifications,
                        onCheckedChange = { viewModel.onNotificationsChange(it) }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked        = uiState.priceAlerts,
                            onCheckedChange = { viewModel.onPriceAlertsChange(it) }
                        )
                        Column {
                            Text(stringResource(R.string.settings_price_alerts),
                                style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.settings_price_alerts_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Límite mensual ────────────────────────────────
                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_budget))
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.settings_budget_limit),
                                style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "$ ${uiState.monthlyLimit.toInt()}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value        = uiState.monthlyLimit,
                            onValueChange = { viewModel.onMonthlyLimitChange(it) },
                            valueRange   = 10000f..200000f,
                            steps        = 18,
                            modifier     = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Ordenar historial ─────────────────────────────
                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_sort_history))
                    sortOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = uiState.selectedSort == option,
                                onClick  = { viewModel.onSortChange(option) }
                            )
                            Text(option, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                // ── Información ───────────────────────────────────
                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_info))
                    SettingsSelectorItem(
                        icon     = Icons.Default.Info,
                        title    = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_version),
                        onClick  = {}
                    )
                    SettingsSelectorItem(
                        icon     = Icons.Default.PrivacyTip,
                        title    = stringResource(R.string.settings_privacy),
                        subtitle = "",
                        onClick  = {}
                    )
                    SettingsSelectorItem(
                        icon     = Icons.Default.Description,
                        title    = stringResource(R.string.settings_terms),
                        subtitle = "",
                        onClick  = {}
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── Helper composable ─────────────────────────────────────────

@Composable
private fun SettingsCard(
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .coloredShadow(
                color        = MaterialTheme.colorScheme.primary,
                borderRadius = 16.dp,
                blurRadius   = 10.dp,
                offsetY      = 2.dp
            )
            .glowBorder(cornerRadius = 16.dp, isDark = isDark),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSelectorItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, name = "Settings Screen")
@Composable
private fun SettingsScreenPreview() {
    SuperAhorroTheme(darkTheme = false) {
        SettingsScreen(onNavigateBack = {})
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsScreen.kt
git commit -m "feat: SettingsScreen visual upgrade - dot pattern + glassmorphism section cards"
```

---

### Task 12: Build completo + push

**Files:** ninguno nuevo — solo verificación

- [ ] **Step 1: Build debug completo**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL` en consola, APK generado en `app/build/outputs/apk/debug/app-debug.apk`.

Si hay errores de compilación:
- `Unresolved reference: getPurchases` → buscar con grep y reemplazar por `refreshPurchases()` o `getPurchasesFlow()`
- `Cannot access class 'PurchaseDao'` → verificar que `DatabaseModule` esté en el paquete `di` y tenga `@InstallIn(SingletonComponent::class)`
- Errores kapt → revisar que `AppDatabase` tenga `exportSchema = false` y `@Database` con las dos entidades

- [ ] **Step 2: Push al remoto**

```bash
git push origin main
```

---

## Criterios de éxito (verificación final)

- [ ] `./gradlew assembleDebug` termina con BUILD SUCCESSFUL
- [ ] Abriendo la app sin red, las compras previas aparecen (Room caché)
- [ ] Al crear/editar/borrar compra, la lista en Home e History se actualiza sin recargar
- [ ] NewPurchase, ProductForm, Profile y Settings tienen fondo con puntos y cards glassmorphism
- [ ] `git log --oneline` muestra commits individuales por cada tarea
