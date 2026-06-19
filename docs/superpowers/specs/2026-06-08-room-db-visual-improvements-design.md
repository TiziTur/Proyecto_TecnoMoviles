# Spec: Room DB + Visual Improvements
**Date:** 2026-06-08  
**Project:** Klarity (Super Ahorro) — Android App  
**Scope:** Segunda Entrega — gap de Room DB + consistencia visual en pantallas de formulario

---

## 1. Contexto

La app ya tiene una arquitectura MVVM sólida con Retrofit + DataStore. Room está en el build.gradle pero sin implementar. Tres pantallas de formulario (NewPurchase, ProductForm, Profile, Settings) tienen fondo blanco plano mientras que Home, Login, History y Stats usan un sistema visual premium (dark bg + dot pattern + glassmorphism cards).

---

## 2. Room DB — Cache-first

### 2.1 Entidades

**PurchaseEntity**
```kotlin
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

**ProductEntity**
```kotlin
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

### 2.2 DAOs

**PurchaseDao**
- `upsertAll(purchases: List<PurchaseEntity>)` — @Insert(onConflict = REPLACE)
- `upsert(purchase: PurchaseEntity)` — @Insert(onConflict = REPLACE)
- `getAll(): Flow<List<PurchaseEntity>>` — @Query SELECT ordered by date DESC
- `getById(id: Int): PurchaseEntity?` — @Query SELECT WHERE id=:id
- `delete(id: Int)` — @Query DELETE WHERE id=:id
- `deleteAll()` — @Query DELETE FROM purchases

**ProductDao**
- `upsertAll(products: List<ProductEntity>)` — @Insert(onConflict = REPLACE)
- `getByPurchaseId(purchaseId: Int): Flow<List<ProductEntity>>` — @Query
- `delete(id: Int)` — @Query DELETE WHERE id=:id
- `deleteByPurchaseId(purchaseId: Int)` — @Query DELETE WHERE purchaseId=:id

### 2.3 AppDatabase

```kotlin
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

Provisto como Singleton en `di/DatabaseModule.kt` (separado de NetworkModule para mantener responsabilidades claras).

### 2.4 Flujo cache-first en repositorios

**PurchaseRepository:**
- `getPurchasesFlow(): Flow<List<Purchase>>` — emite desde Room (inmediato para la UI)
- `refreshPurchases()` — llama API, guarda resultado en Room, Room Flow re-emite solo
- `createPurchase(...)` — llama API → si OK, hace upsert en Room → retorna resultado
- `updatePurchase(...)` — llama API → si OK, actualiza en Room
- `deletePurchase(id)` — llama API → si OK, borra de Room

**HomeViewModel / HistoryViewModel:** colectan `getPurchasesFlow()` (reactivo) y llaman `refreshPurchases()` al cargar.

**PurchaseDetailViewModel:** lee `getById()` + `getByPurchaseId()` desde Room.

**ProductRepository:**
- `createProduct(...)` — llama API → si OK, upsert en Room
- `updateProduct(...)` — llama API → si OK, update en Room
- `deleteProduct(id, purchaseId)` — llama API → si OK, borra de Room (Room lo propaga por CASCADE)

### 2.5 Migración de ViewModels

Los ViewModels que usan `viewModelScope.launch { repo.getPurchases() }` pasan a `repo.getPurchasesFlow().collect { ... }` para reactividad automática.

---

## 3. Mejoras Visuales

### 3.1 Pantallas a modificar

| Pantalla | Cambio |
|---|---|
| `NewPurchaseScreen` | Fondo dark + dot pattern + formulario en glass card |
| `ProductFormScreen` | Igual que NewPurchase |
| `ProfileScreen` | Fondo dark + dot pattern + hero card para avatar + glass card para campos |
| `SettingsScreen` | Fondo dark + dot pattern + secciones en glass cards agrupadas |

### 3.2 Patrón a aplicar (ya existe en LoginScreen/HomeScreen)

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .dotPatternBackground(...)
) {
    // Card glassmorphism wrapping form content
    Card(
        modifier = Modifier
            .coloredShadow(color = primary, ...)
            .glowBorder(cornerRadius = 20.dp, isDark = isDark),
        ...
    ) { /* form fields */ }
}
```

### 3.3 TopBar transparente

Las pantallas modificadas usarán `TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)` para integrar visualmente con el fondo oscuro.

### 3.4 Consistencia de iconos en campos

Cada `OutlinedTextField` mantiene sus `leadingIcon`. En modo dark, los campos usan el mismo `MaterialTheme.colorScheme.surface` con `borderColor = MaterialTheme.colorScheme.outline`.

---

## 4. Archivos a crear/modificar

### Nuevos archivos
- `data/local/db/PurchaseEntity.kt`
- `data/local/db/ProductEntity.kt`
- `data/local/db/PurchaseDao.kt`
- `data/local/db/ProductDao.kt`
- `data/local/db/AppDatabase.kt`
- `di/DatabaseModule.kt`

### Archivos modificados
- `data/repository/PurchaseRepository.kt` — cache-first logic
- `data/repository/ProductRepository.kt` — idem
- `ui/screens/purchase/NewPurchaseScreen.kt` — visual upgrade
- `ui/screens/product/ProductFormScreen.kt` — visual upgrade
- `ui/screens/profile/ProfileScreen.kt` — visual upgrade
- `ui/screens/settings/SettingsScreen.kt` — visual upgrade
- `ui/screens/home/HomeViewModel.kt` — usar Flow desde Room
- `ui/screens/history/HistoryViewModel.kt` — idem

---

## 5. Criterios de éxito

- [ ] Room DB compila sin errores (kapt)
- [ ] Datos persisten offline tras cerrar/abrir app
- [ ] Las 4 pantallas modificadas tienen dot pattern + glassmorphism
- [ ] Build release y debug sin warnings críticos
- [ ] Todas las pantallas de la segunda entrega están cubiertas

---

## 6. Fuera de scope

- Testing unitario de DAOs
- Sincronización bidireccional / conflict resolution
- Migración de esquema Room (solo version=1)
