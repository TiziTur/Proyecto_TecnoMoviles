# Diseño — Etapa Final SUPER AHORRO (Klarity)

**Fecha:** 2026-06-18  
**Proyecto:** Super Ahorro / Klarity — Trabajo Práctico Integrador Tecnologías Móviles 2026  
**Alcance:** Cuatro funcionalidades nuevas que completan la etapa final de la consigna.

---

## Contexto

La app ya tiene implementado: CRUD compras/productos (Room + Railway/PostgreSQL), chat IA con Gemini, escaneo de tickets con Gemini Vision + ML Kit, comparativa de precios (datos hardcodeados), biometría, WorkManager worker para alertas de precio (no programado). La etapa final agrega las cuatro funcionalidades descritas abajo.

---

## 1. Comparativa de precios con datos reales (SEPA / Precios Claros)

### Problema actual
`/prices/compare` devuelve 25 productos hardcodeados en TypeScript. No usa datos reales.

### API de referencia
El gobierno argentino publica el dataset SEPA (Sistema Electrónico de Publicidad de Precios Argentinos) en `https://datos.produccion.gob.ar/dataset/sepa-precios` como ZIPs diarios. No hay API queryable; los datos se descargan y parsean localmente.

La API directa de preciosclaros.gob.ar (`d3e6htiiul5ek9.cloudfront.net`) está deprecada y devuelve error. El enfoque elegido es consumir el ZIP de SEPA, almacenar en PostgreSQL y servir desde ahí.

### Cambios en backend

**Nueva tabla en schema.sql:**
```sql
CREATE TABLE IF NOT EXISTS reference_prices (
  id           SERIAL PRIMARY KEY,
  product_name TEXT NOT NULL,
  brand        TEXT,
  supermarket  TEXT NOT NULL,
  price        NUMERIC(10,2) NOT NULL,
  province     TEXT,
  updated_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ref_prices_name ON reference_prices (LOWER(product_name));
```

**Nuevo archivo `backend/src/seeds/sepaImport.ts`:**
- Descarga el ZIP del día actual desde `datos.produccion.gob.ar` (URL obtenida via CKAN package_show)
- Usa `unzipper` + `csv-parse` (npm) para parsear el CSV dentro del ZIP
- Filtra las primeras 5.000 filas con precio > 0
- Hace UPSERT en `reference_prices` agrupando por nombre+supermercado
- Se ejecuta como script standalone: `npm run seed:sepa`

**Modificación a `backend/src/routes/prices.ts`:**
- `GET /prices/compare?query=<nombre>` busca `ILIKE '%query%'` en `reference_prices`
- Devuelve array de `{ product_name, brand, supermarket, price, updated_at }`
- Si no hay query, devuelve los 50 productos más recientes
- Si la tabla está vacía, devuelve 200 con `{ data: [], source: "empty", message: "Ejecutar seed:sepa" }`
- Header de respuesta incluye `X-Data-Source: SEPA-preciosclaros.gob.ar`

**Nuevas dependencias backend:** `unzipper`, `csv-parse`, `node-fetch` (si no existe).

### Cambios en Android

**`PriceComparisonScreen.kt`:**
- Agrega un `TextField` de búsqueda (debounce 500ms) que llama al nuevo endpoint
- Muestra "Fuente: SEPA / Precios Claros — actualizado: [fecha]" al pie de la lista
- Si la tabla está vacía muestra mensaje explicativo en lugar de error

**`AiDtos.kt`:** Actualizar `PriceComparisonResponse` para incluir `brand`, `updatedAt`, `source`.

**`ApiService.kt`:** Agregar parámetro `query: String?` al endpoint de precios.

---

## 2. Exportación de datos a CSV

### Flujo
Desde `HistoryScreen`, el usuario presiona un botón "Exportar CSV". La app:
1. Lee todas las compras + productos desde Room (ya en memoria en `HistoryViewModel`)
2. Genera el CSV en memoria como `String`
3. Lo escribe en `context.filesDir/exports/compras_<fecha>.csv` usando `FileProvider`
4. Dispara `Intent(ACTION_SEND, type = "text/csv")` con el URI del archivo

No requiere permiso `WRITE_EXTERNAL_STORAGE` (usa almacenamiento privado de la app + FileProvider). No necesita cambios en backend.

### Formato CSV
```
"Fecha","Hora","Supermercado","Total","Cod Producto","Nombre","Descripcion","Precio","Cantidad"
"2026-06-15","14:30","Carrefour","5420.50","7891234","Leche La Serenísima","1L entera","850.00","2"
```
Una fila por producto; los campos de compra se repiten en cada fila del producto.

### Archivos nuevos/modificados — Android
- **`HistoryViewModel.kt`**: `exportToCsv(context: Context)` — genera CSV, devuelve `Uri`
- **`HistoryScreen.kt`**: Botón "Exportar" en la TopBar con ícono de share; lanza el Intent

### FileProvider
- Agregar `<provider>` en `AndroidManifest.xml`
- Agregar `res/xml/file_paths.xml` con `<files-path name="exports" path="exports/"/>`

---

## 3. Filtros avanzados en Historial

### Filtros disponibles
| Filtro | Tipo | UI |
|--------|------|----|
| Supermercado | Texto libre (autocomplete con los de las compras) | DropdownMenu |
| Fecha desde | LocalDate | DatePickerDialog |
| Fecha hasta | LocalDate | DatePickerDialog |
| Monto mínimo | Float | TextField numérico |
| Monto máximo | Float | TextField numérico |

### Arquitectura
Los filtros viven en `HistoryViewModel` como `StateFlow`. El filtrado se aplica **en memoria** sobre la lista ya cargada de Room (no requiere queries SQL adicionales — Room ya tiene todos los datos del usuario).

```kotlin
// En HistoryViewModel
val filters = MutableStateFlow(PurchaseFilters())
val filteredPurchases = combine(purchasesFlow, filters) { purchases, f ->
    purchases.filter { p ->
        (f.supermarket == null || p.supermarket.contains(f.supermarket, ignoreCase = true)) &&
        (f.dateFrom == null || p.date >= f.dateFrom) &&
        (f.dateTo == null || p.date <= f.dateTo) &&
        (f.minAmount == null || p.total >= f.minAmount) &&
        (f.maxAmount == null || p.total <= f.maxAmount)
    }
}.stateIn(...)
```

### UI
- Panel expandible/colapsable encima de la lista (animado con `AnimatedVisibility`)
- Botón "Filtros" en la TopBar con badge de cantidad de filtros activos
- Botón "Limpiar" dentro del panel para resetear todos los filtros
- Chips de filtros activos visibles sobre la lista cuando el panel está cerrado

### Archivos modificados — Android
- **`HistoryViewModel.kt`**: Agregar `PurchaseFilters` data class + lógica de filtrado
- **`HistoryScreen.kt`**: Agregar `FilterPanel` composable + chips de filtros activos

---

## 4. WorkManager scheduling — Toggle en Settings

### Estado actual
`PriceAlertWorker` existe y está completo. No hay código que lo programe (`enqueueUniquePeriodicWork`).

### Diseño
- Nueva clave en `DataStore`: `price_alerts_enabled: Boolean` (default `false`)
- `SettingsViewModel` expone `priceAlertsEnabled: StateFlow<Boolean>` y `togglePriceAlerts(enabled: Boolean)`
- Al activar: `WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, request24h)`
- Al desactivar: `WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)`
- `SuperAhorroApp.kt`: Al iniciar, si `priceAlertsEnabled == true` y el work no está ya encolado, lo programa

### UI en SettingsScreen
```
[🔔] Alertas de precio          [Switch]
     Te avisamos cuando un producto
     registrado baje de precio
```

### Archivos modificados — Android
- **`SessionDataStore.kt`** (o `ThemeDataStore.kt`): Agregar clave `price_alerts_enabled`
- **`SettingsViewModel.kt`**: Lógica de toggle + `WorkManager`
- **`SettingsScreen.kt`**: Row con Switch + descripción
- **`SuperAhorroApp.kt`**: Wiring inicial en `onCreate`

---

## Orden de implementación recomendado

1. **Backend primero**: Tabla SEPA + seed script + endpoint actualizado (sin esto, el precio real no funciona en Android)
2. **Precios en Android**: Actualizar `PriceComparisonScreen` + DTOs
3. **WorkManager toggle**: Cambio aislado, sin dependencias entre features
4. **Filtros en Historial**: Cambio aislado en HistoryViewModel + HistoryScreen
5. **Exportación CSV**: Requiere FileProvider en Manifest + nuevo código en HistoryViewModel

---

## Checklist de completitud de la consigna

| Requisito consigna | Estado |
|--------------------|--------|
| Carga automática del ticket con IA | ✅ Ya implementado (Gemini Vision backend + Android) |
| OCR del ticket | ✅ Ya implementado (ML Kit fallback) |
| Chat para consultas sobre historial | ✅ Ya implementado |
| Comparativa de precios entre supermercados | 🔄 Mejorado con datos reales SEPA |
| Notificaciones | 🔄 WorkManager worker ya existe, se agrega scheduling |
| Exportación de datos | ✅ Nuevo (CSV + Intent share) |
| Filtros avanzados | ✅ Nuevo (en HistoryScreen) |
| Autenticación biométrica | ✅ Ya implementado |
| Sincronización en la nube | ✅ Ya implementado (Railway + PostgreSQL) |
