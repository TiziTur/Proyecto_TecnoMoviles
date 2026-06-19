# Segunda Entrega — SUPER AHORRO (Klarity)

Resumen de todo lo implementado para la segunda entrega del Trabajo Práctico Integrador de Tecnologías Móviles, con foco en el stack utilizado, las decisiones de arquitectura tomadas y cómo funciona la persistencia local (Room) combinada con el backend remoto (Railway).

---

## 1. Objetivo de la segunda entrega

Según la consigna, la segunda entrega debía incorporar **funcionalidad real** sobre la base visual de la primera entrega:

- Persistencia local de sesión/preferencias.
- Base de datos local para compras y productos.
- Operaciones con corrutinas.
- Networking real contra un backend.
- Menús y diálogos.
- Carga real de datos (no mockeados).
- Al menos una funcionalidad con Intents.

Todo esto se cumplió y se fue extendiendo con funcionalidades adicionales (estadísticas avanzadas, IA/OCR para tickets, chat, alertas de precio, biometría).

---

## 2. Stack tecnológico

| Capa | Tecnología | Notas |
|------|-----------|-------|
| Lenguaje | Kotlin 2.0.21 | |
| UI | Jetpack Compose + Material 3 | Theming claro/oscuro, tipografía M3 |
| Arquitectura | MVVM | ViewModels con `StateFlow`/`collectAsStateWithLifecycle` |
| Inyección de dependencias | Hilt 2.51.1 | Módulos `NetworkModule` y `DatabaseModule` |
| Navegación | Navigation Compose 2.8.4 | Rutas tipadas en `Routes.kt` |
| Persistencia local (preferencias) | Jetpack DataStore | `SessionDataStore`, `ThemeDataStore` |
| Base de datos local | Room | Entidades `PurchaseEntity`, `ProductEntity` |
| Networking | Retrofit + OkHttp (logging interceptor) + Gson | `ApiService` + DTOs |
| Backend | Node.js + Express + TypeScript | Carpeta `backend/` |
| Base de datos remota | PostgreSQL en Railway | Vía `pg` Pool |
| Gráficos | Vico (Compose) + `Canvas` custom | Gráficos de barras/segmentos en Estadísticas |
| Imágenes | Coil | Carga de imagen del ticket |
| OCR | ML Kit Text Recognition | Fallback para lectura de tickets |
| Background work | WorkManager + Hilt Work | Alertas de precio periódicas |
| Biometría | AndroidX Biometric | Autenticación biométrica opcional |
| Build | AGP 8.7.3 / Gradle 9.3.1 | `minSdk` 26, `compileSdk`/`targetSdk` 35 |

---

## 3. Arquitectura general (MVVM + repositorios "cache-first")

```
UI (Compose Screens)
   ↓ collectAsStateWithLifecycle
ViewModel (StateFlow<UiState>)
   ↓
Repository (PurchaseRepository, ProductRepository, AuthRepository, SupermarketRepository)
   ├── Room (AppDatabase → PurchaseDao / ProductDao)   ← fuente de verdad local
   └── Retrofit ApiService → Backend (Railway)         ← sincronización remota
```

**Decisión clave: arquitectura "cache-first" con Room como fuente de verdad para la UI.**

- Las pantallas (`HomeScreen`, `HistoryScreen`, etc.) observan **Flows de Room** (`purchaseDao.getAll()` mapeado a `Flow<List<Purchase>>`), no respuestas directas de la API.
- Los repositorios exponen métodos `refreshXxx()` que llaman a la API y, si la respuesta es exitosa, hacen `upsert`/`upsertAll` en Room.
- Como la UI observa Room mediante `Flow`, cualquier escritura (ya sea por refresh desde el backend o por una operación local) actualiza la UI automáticamente — **reactividad de punta a punta** sin necesidad de refrescar manualmente cada pantalla.

### Por qué esta decisión

- Cumple el requisito de "base de datos local" de forma genuina (no solo como caché decorativo): la app sigue siendo usable (lectura) sin conexión, ya que la UI lee de Room.
- Evita duplicar lógica de "estado de carga" en cada ViewModel: el ViewModel simplemente combina el `Flow` de Room con el estado de carga/error de la llamada de red.
- Simplifica la sincronización: cualquier escritura exitosa en el backend (`create`, `update`, `delete`, `get`) se refleja también en Room, manteniendo ambas fuentes alineadas.

---

## 4. Base de datos local (Room)

### 4.1 Entidades

- `PurchaseEntity` — id, fecha, hora, supermercado, total, cantidad de productos.
- `ProductEntity` — id, id de compra asociada, código, nombre, descripción, precio, cantidad.

### 4.2 `AppDatabase`

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

Provista vía Hilt en `di/DatabaseModule.kt` como singleton.

### 4.3 DAOs

- `PurchaseDao` y `ProductDao` exponen:
  - `getAll(): Flow<List<Entity>>` — para que la UI sea reactiva.
  - `upsert(entity)` / `upsertAll(list)` — para sincronizar datos que llegan del backend.
  - `delete(id)` — para mantener Room sincronizado tras un borrado remoto exitoso.

### 4.4 Flujo típico (ejemplo: compras)

1. `HomeViewModel`/`HistoryViewModel` se suscriben a `purchaseRepository.getPurchasesFlow()` (Room).
2. Al iniciar la pantalla (o con pull-to-refresh), se llama a `refreshPurchases()`:
   - Pide el token de sesión a `SessionDataStore`.
   - Llama a `ApiService.getPurchases(token)`.
   - Si la respuesta es exitosa, mapea los DTOs a `PurchaseEntity`/`ProductEntity` y hace `upsertAll` en Room.
3. Como la UI ya está observando el `Flow` de Room, se actualiza sola — no hace falta "devolver" los datos nuevos al ViewModel manualmente.
4. Las operaciones de escritura (`createPurchase`, `updatePurchase`, `deletePurchase`) siguen el mismo patrón: llaman a la API y, si tiene éxito, reflejan el cambio en Room (`upsert` o `delete`).

### 4.5 Manejo de errores

Todas las operaciones de red están envueltas en `runCatching { ... }.getOrElse { ApiResult.Error(...) }`, devolviendo un `ApiResult<T>` (`Success`/`Error`) que el ViewModel traduce a mensajes de error visibles en la UI (Snackbars/diálogos), cumpliendo el requisito de "errores visibles en formularios".

---

## 5. Persistencia de sesión y preferencias (DataStore)

- **`SessionDataStore`**: guarda el token JWT (`bearerToken`) y datos básicos del usuario logueado. Se usa en todos los repositorios para autenticar las llamadas a la API (`Authorization: Bearer <token>`).
- **`ThemeDataStore`**: persiste la preferencia de modo oscuro/claro, para que sobreviva a reinicios de la app.

### Decisión: DataStore en vez de SharedPreferences

Se eligió **Jetpack DataStore** (en lugar de `SharedPreferences`) porque:
- Es la alternativa moderna recomendada por Android.
- Expone los valores como `Flow`, lo que permite que el modo oscuro o el estado de sesión se reflejen reactivamente en la UI sin polling.
- Evita el manejo de I/O síncrono bloqueante propio de `SharedPreferences`.

---

## 6. Backend (Node.js + Express + TypeScript) y Railway

### 6.1 Por qué un backend propio

La consigna pedía "consumir una API externa o simulada". Se optó por **construir un backend propio** en lugar de usar una API pública de terceros, porque:
- Permite modelar exactamente las entidades del dominio (usuarios, compras, productos, supermercados, precios, tickets, chat) con autenticación real.
- Habilita funcionalidades opcionales de la etapa final (IA para tickets, chat de consultas) que requieren lógica de servidor (por ejemplo, llamadas a un proveedor de IA con la clave protegida del lado del servidor).
- Da control total sobre el esquema de base de datos para que coincida 1:1 con las entidades de Room/DTOs de Android.

### 6.2 Estructura

```
backend/
├── schema.sql               # Esquema PostgreSQL (usuarios, compras, productos, etc.)
├── src/
│   ├── db.ts                 # Pool de conexión a PostgreSQL (vía pg)
│   ├── index.ts              # Punto de entrada Express
│   ├── middleware/auth.ts    # Verificación de JWT
│   └── routes/
│       ├── auth.ts           # Login / registro
│       ├── users.ts          # Perfil de usuario
│       ├── purchases.ts      # CRUD de compras + productos asociados
│       ├── products.ts       # CRUD de productos
│       ├── supermarkets.ts    # Listado de supermercados
│       ├── prices.ts         # Precios de referencia / comparativas
│       ├── chat.ts           # Endpoint de chat (consultas sobre historial)
│       └── ticket.ts         # Procesamiento de tickets (IA/OCR)
```

### 6.3 Base de datos remota: PostgreSQL en Railway

```typescript
// backend/src/db.ts
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false
});
```

- **Railway** provee automáticamente la variable de entorno `DATABASE_URL` para la instancia de PostgreSQL.
- En producción se habilita `ssl: { rejectUnauthorized: false }` porque Railway expone Postgres con certificados que no siempre validan contra la CA por defecto de Node.
- El backend completo (Express + Postgres) está desplegado en Railway, en la URL:
  `https://proyectotecnomoviles-production.up.railway.app/`

### 6.4 Autenticación

- Login/registro devuelven un **JWT**, validado por `middleware/auth.ts` en cada endpoint protegido.
- Android guarda ese JWT en `SessionDataStore` y lo envía como `Bearer token` en cada request vía `ApiService`.

### 6.5 Conexión Android → Railway

```kotlin
// di/NetworkModule.kt
private const val BASE_URL = "https://proyectotecnomoviles-production.up.railway.app/"
```

- **Retrofit** + **Gson** para serializar/deserializar DTOs.
- **OkHttp** con `HttpLoggingInterceptor` (nivel `BODY`) para depurar requests/responses durante el desarrollo, y timeouts de conexión/lectura de 30s.
- DTOs separados por dominio (`PurchaseDtos`, `AuthDtos`, `AiDtos`) para no acoplar los modelos de UI/Room directamente al contrato de red — cada repositorio mapea DTO ↔ Entity ↔ modelo de dominio.

---

## 7. Funcionalidades destacadas implementadas

### 7.1 Gestión de compras y productos (CRUD completo)

- Alta, edición y borrado de compras y productos, con sincronización Room ↔ Backend ↔ Railway/Postgres.
- `ProductRepository` escribe en Room en cada llamada exitosa a la API, igual que `PurchaseRepository`.

### 7.2 Historial y Home reactivos

- `HistoryViewModel` y `HomeViewModel` se reescribieron para ser **reactivos vía `Flow` de Room** (commits `73825ad`, `b4f6f3d`), eliminando la necesidad de recargar manualmente tras cada operación.

### 7.3 Estadísticas avanzadas

Se rediseñó por completo la pantalla de Estadísticas en 4 pestañas (commit `a703df3` y trabajo asociado):

- **General**: evolución mensual del gasto (gráfico Canvas), comparación con el mes anterior.
- **Presupuesto**: progreso del presupuesto mensual (componente `SegmentBar` propio, de una sola pieza, para evitar el "doble barra" que genera `LinearProgressIndicator` en Material3 1.3+), proyección de fin de mes, gasto por día de la semana.
- **Supermercados**: gasto por supermercado y ticket promedio (`SegmentBar` + cálculo de promedio).
- **Productos**: ranking de productos más comprados, productos con mayor aumento de precio, frecuencia de compra e ítems promedio por compra.

Todos los cálculos (proyección de presupuesto, comparación mensual, gasto por día de semana, detección de aumentos de precio, frecuencia de compra) se implementaron en `StatsViewModel` a partir de los datos ya sincronizados en Room.

### 7.4 Intents

- **Compartir compra**: desde `PurchaseDetailScreen`, se usa `Intent(ACTION_SEND)` + `createChooser` para compartir el detalle de una compra (texto) por cualquier app instalada (WhatsApp, email, etc.).
- **Cámara/Galería para ticket**: selección/captura de imagen del ticket de compra (`MediaStore`/contratos de actividad de Compose), asociándola a la compra.

### 7.5 Funcionalidades de la etapa final (adelantadas)

Aunque corresponden a la "etapa final", varias se incorporaron ya en este ciclo:

- **OCR de tickets** con ML Kit Text Recognition como fallback cuando no se usa IA.
- **Carga del ticket con IA**: endpoint `backend/src/routes/ticket.ts` que procesa la imagen del lado del servidor y devuelve los datos estructurados de la compra.
- **Chat de consultas**: `ChatScreen`/`ChatViewModel` + `backend/src/routes/chat.ts`, para preguntar por consumos/precios históricos.
- **Alertas de precio**: `PriceAlertWorker` (WorkManager + Hilt Work) corre periódicamente y notifica si un producto subió de precio, usando los datos de `prices.ts` / Room.
- **Autenticación biométrica**: `BiometricHelper` con AndroidX Biometric, como capa opcional de seguridad para acceder a la app.

### 7.6 Internacionalización

- Soporte completo español/inglés (`values/strings.xml` y `values-en/strings.xml`), incluyendo todas las strings nuevas agregadas para Estadísticas, asegurando que ninguna pantalla quede con texto hardcodeado.

### 7.7 Modo oscuro y diseño visual

- Theming Material 3 completo (paleta Klarity, claro/oscuro) persistido vía `ThemeDataStore`.
- Rediseño visual "premium" aplicado a todas las pantallas: fondo con patrón de puntos (`dotPatternBackground`), tarjetas con efecto *glassmorphism*, sombras de color (`coloredShadow`), bordes con brillo (`glowBorder`).

---

## 8. Decisiones importantes — resumen

| Decisión | Alternativa descartada | Motivo |
|----------|------------------------|--------|
| Room como fuente de verdad de la UI (cache-first) + Flow | Llamar a la API directamente desde el ViewModel en cada pantalla | Reactividad automática, funcionamiento offline para lectura, menos lógica repetida de "loading" |
| Backend propio (Node + Express + TS + Postgres) en Railway | Consumir una API pública externa | Control total del modelo de datos, necesario para IA/OCR/chat y autenticación con JWT propio |
| DataStore (no SharedPreferences) | SharedPreferences | API reactiva basada en `Flow`, recomendación moderna de Android |
| Retrofit + Gson + OkHttp con logging interceptor | Ktor client / fetch manual | Estándar de la industria, fácil integración con corrutinas y Hilt |
| DTOs separados de entidades Room y modelos de dominio | Reusar un mismo modelo para red, DB y UI | Evita acoplar el contrato de red con el esquema local; cada capa puede evolucionar independientemente |
| `SegmentBar` propio en vez de `LinearProgressIndicator` | Usar el componente estándar de Material3 | M3 1.3+ dibuja el track y el relleno con un gap/indicador de stop que se veía como "dos barras"; se implementó un único `Box` recortado con gradiente |
| SSL `rejectUnauthorized: false` solo en producción contra Postgres de Railway | Desactivar SSL globalmente o exigir CA estricta | Railway expone Postgres con certificados que Node no valida por defecto; se limita el relajo de seguridad solo al entorno productivo de Railway |

---

## 9. Cómo ejecutar todo

### App Android
```bash
./gradlew assembleDebug
```
El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. La app apunta directamente al backend de Railway (no requiere levantar nada local para probar funcionalidad básica).

### Backend (desarrollo local, opcional)
```bash
cd backend
cp .env.example .env   # completar DATABASE_URL (Railway) y JWT_SECRET
npm install
npm run dev
```
