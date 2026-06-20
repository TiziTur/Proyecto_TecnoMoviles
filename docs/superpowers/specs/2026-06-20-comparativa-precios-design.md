# Comparativa de precios — Ticket→Seed matching y comparación de compra — Design Spec

Ciclo 1 de 3 (comparativa de precios → asistente IA → biometría). Cada ciclo se diseña, planea e implementa por separado porque el motor de comparación construido aquí lo reutiliza el ciclo 2 (asistente IA).

## Contexto y objetivo

Hoy la app ya tiene tres piezas construidas pero desconectadas entre sí:

1. **Escaneo de ticket con IA** (`ticket.ts` + `PurchaseDetailViewModel`): Gemini Vision extrae productos de la foto del ticket con la nomenclatura del local (ej. "Coca-Cola 1.5L"), el usuario confirma en un diálogo y se guardan como `Product` libres, sin ningún vínculo a la seed.
2. **Catálogo de precios de referencia** (`prices.ts` + `PriceComparisonScreen`): lista paginada/filtrable de `reference_prices` (datos oficiales SEPA), con búsqueda, categorías, marcas y orden por precio — ya muy completa.
3. **Comparación de una compra contra otros supermercados** (`purchaseComparison.ts`): endpoint backend completo que matchea por tokens los productos de una compra contra `reference_prices` y calcula el total que hubiera costado en cada supermercado — **sin ninguna pantalla Android que lo use**.

El objetivo de este ciclo es conectar las tres: que el escaneo de ticket vincule explícitamente cada producto a su entrada en la seed (cuando exista), que el usuario pueda corregir ese vínculo a mano, que exista una pantalla para ver la comparación de una compra puntual contra otros supermercados, y pulir el listado del catálogo (nombres + iconos).

## Alcance

- Backend: nueva columna `products.seed_product_name`, nuevo paso de matching tras `scan-ticket`, ajuste de `purchaseComparison.ts` para usar el vínculo exacto cuando existe (fallback a la heurística de tokens para productos no vinculados), limpieza de nombres extendida en `prices.ts`.
- Android: nueva pantalla de confirmación de ticket (reemplaza el diálogo actual), nueva pantalla `PurchaseComparisonScreen`, mapa categoría→ícono Material en `PriceComparisonScreen`.
- No se toca el chat/asistente IA ni la biometría (ciclos 2 y 3).
- No se normaliza `reference_prices` a una tabla de catálogo con ID propio — el nombre de texto sigue siendo la clave de matching, consistente con el resto del sistema.

## 1. Vínculo producto↔seed (esquema y matching)

### Esquema

```sql
ALTER TABLE products ADD COLUMN seed_product_name TEXT;
```

`NULL` significa "no vinculado a ningún producto de la seed" (no comparable). Cuando tiene valor, es exactamente igual a un `reference_prices.product_name` existente — permite hacer `JOIN`/`WHERE` exacto en lugar de heurística por tokens.

### Matching automático

Tras `POST /purchases/:id/scan-ticket` devolver los productos parseados por Gemini, el cliente llama a un nuevo endpoint:

```
POST /products/match-seed
Body: { products: [{ name: string }] }
Response: { matches: [{ seedMatch: string | null, candidates: string[] }] }
```

Por cada producto, se reutiliza `tokenize()`/`scoreMatch()` de `purchaseComparison.ts` contra `reference_prices`, agrupando por `product_name` para no repetir candidatos por supermercado. Se considera **auto-match** (`seedMatch` no nulo) cuando el mejor candidato cubre ≥70% de los tokens del nombre del ticket y tiene al menos 2 tokens en común. Si no se cumple, `seedMatch` es `null` y se devuelven hasta 5 `candidates` (nombres de producto de la seed con mejor score) para que el usuario elija manualmente.

Este endpoint es liviano (solo SQL, sin llamar a Gemini de nuevo) y se ejecuta automáticamente apenas vuelve la respuesta de `scan-ticket`, antes de mostrar la pantalla de confirmación.

### Uso del vínculo en la comparación

`purchaseComparison.ts` se ajusta: para cada producto de la compra, si `seed_product_name IS NOT NULL`, se buscan directamente las filas de `reference_prices` con ese `product_name` exacto (rápido y preciso). Si es `NULL`, se mantiene el fallback actual de matching por tokens (por si el usuario no confirmó/vinculó manualmente, sigue habiendo algo de comparación, aunque menos precisa).

## 2. Pantalla de confirmación de ticket

Se reemplaza `TicketScanConfirmDialog` (AlertDialog) por una pantalla completa nueva, `TicketConfirmScreen`, navegada tras un escaneo exitoso (ruta `purchase/{id}/ticket-confirm`, con el resultado del escaneo pasado vía un estado compartido del ViewModel en vez de argumentos de navegación serializados).

Por cada producto extraído, una fila con:
- Nombre, precio unitario, cantidad (editables igual que hoy, si ya era posible).
- Chip de estado de vínculo:
  - ✓ verde **"Vinculado a {nombre seed}"** cuando hay auto-match.
  - ⚠ ámbar **"Sin coincidencia"** con acción "Buscar" cuando no hay match.
- Tocar el chip (vinculado o no) abre un buscador inline (reutiliza `GET /prices/compare?query=` ya existente) listando candidatos para elegir el vínculo correcto, o la opción "No vincular".

Botón **"Confirmar y guardar"** dispara `confirmScannedProducts`, que ahora también persiste `seedProductName` por producto (cadena completa hasta Room/backend, ver más abajo).

### Cambios de datos para transportar el vínculo

- `ScannedProductDto` (Android, `AiDtos.kt`) y el `ParsedProduct` que ya devuelve `match-seed` ganan `seedProductName: String?` y `seedCandidates: List<String>` (solo se usan en memoria durante la confirmación, no se persisten los candidatos).
- `CreateProductRequest` / `ProductDto` / `UpdateProductRequest` (`PurchaseDtos.kt`) y `ProductEntity` ganan `seedProductName: String?`.
- `ProductRepository.createProduct(...)` gana el parámetro `seedProductName: String? = null`.
- Backend `POST /purchases/:id/products` acepta y guarda `seed_product_name`; el `SELECT`/`UPDATE` de `products.ts` se actualiza para incluir la columna en las proyecciones explícitas.
- `model.Product` (UI) no necesita el campo — solo se usa para mostrar comparabilidad en la propia pantalla de detalle si se quiere (opcional, no bloqueante para este ciclo).

Se requiere una migración Room (`AppDatabase` versión +1) para la columna nueva en `ProductEntity`.

## 3. Pantalla "Comparar compra" — ya implementada (verificado durante el plan)

Al planificar la implementación se descubrió que esta pieza **ya existe completa y funcionando**: `PurchaseComparisonScreen.kt` + `PurchaseComparisonViewModel.kt` (en `ui/screens/purchase/`), con ruta `Routes.PurchaseComparison`, wireada en `NavGraph.kt`, y un botón de entrada (ícono `CompareArrows`, tooltip "Comparar precios") ya presente en `PurchaseDetailScreen.kt:173-175`. Implementa exactamente lo que describía esta sección: header con total real, ranking de supermercados de más barato a más caro con ahorro %, tarjetas expandibles con detalle producto por producto, y sección de productos sin match. `ApiService.comparePurchase()` y el DTO `PurchaseComparisonResponse` también ya existen.

**No hay trabajo nuevo de UI/pantalla en esta sección.** El único impacto de este ciclo sobre esta pantalla es indirecto: una vez que `purchaseComparison.ts` (backend) empiece a usar `seed_product_name` para hacer el join exacto (sección 1), los resultados que ya muestra esta pantalla serán más precisos para los productos vinculados — sin cambiar el código Android de la pantalla en absoluto.

## 4. Pulido del listado de comparativa de precios

### Nombres descriptivos

Se extiende `cleanProductName()` en `prices.ts`:
- Detecta y normaliza el formato/tamaño al final del nombre (ej. `X1.5LT` → `1.5L`, `X500GR` → `500g`, `X12X355ML` → `12x355ml`) vía una tabla de patrones regex → reemplazo.
- Si `brand` está presente y ya aparece dentro del nombre crudo, no se duplica; la marca se sigue mostrando aparte en la card (campo ya existente en la respuesta).

### Iconos por categoría

Se agrega un mapa `categoryIcon: Map<String, ImageVector>` en `PriceComparisonScreen.kt` (Material Icons Extended, ya en uso en el proyecto, sin nuevas dependencias), por ejemplo:

| Categoría | Ícono |
|---|---|
| Bebida | `Icons.Default.LocalDrink` |
| Lácteo | `Icons.Default.Icecream` (o `LocalCafe` si se prefiere algo más "lácteo") |
| Mascotas | `Icons.Default.Pets` |
| Bebé | `Icons.Default.ChildCare` |
| Papel | `Icons.Default.Inventory2` |
| Limpieza | `Icons.Default.CleaningServices` |
| Perfumería | `Icons.Default.Spa` |
| Carne y Fiambre | `Icons.Default.LunchDining` |
| Panadería | `Icons.Default.BakeryDining` |
| Golosinas | `Icons.Default.Cookie` |
| Snack | `Icons.Default.Fastfood` |
| Aceite | `Icons.Default.WaterDrop` |
| Condimento | `Icons.Default.Restaurant` |
| Enlatado | `Icons.Default.Inventory` |
| Congelado | `Icons.Default.AcUnit` |
| Cereales | `Icons.Default.BreakfastDining` |
| Almacén | `Icons.Default.ShoppingBasket` |
| Alimento (fallback) | `Icons.Default.LocalGroceryStore` |

El ícono reemplaza/acompaña el swatch de color actual en `CompactPriceCard` y en los `FilterChip` de categoría (color de fondo + ícono del mismo tono, en vez de solo color).

(La lista exacta de íconos se termina de ajustar durante la implementación si algún nombre de `Icons.Default.*` no existe en la versión de Material Icons Extended del proyecto — se verifica al compilar.)

## Errores y casos límite

- Si `match-seed` falla (red, 500) tras un escaneo exitoso: se muestra la pantalla de confirmación igual, todos los productos en estado "Sin coincidencia" (degradación, no bloqueo).
- Si el usuario no vincula nada manualmente, los productos se guardan igual (comportamiento actual preservado) — solo que `seed_product_name` queda `NULL` y la comparación posterior cae al fallback por tokens.
- `PurchaseComparisonScreen` con una compra sin ningún producto: estado vacío explicando que hay que escanear/agregar productos primero.
- `PurchaseComparisonScreen` cuando ningún supermercado tiene match alguno: estado vacío "no hay datos de referencia para comparar esta compra todavía".

## Testing

- Backend: tests de `match-seed` con nombres ya conocidos del seed (debe auto-matchear) y nombres inventados (debe devolver `null` + candidatos razonables).
- Backend: test de `purchaseComparison.ts` con productos mezclando `seed_product_name` set/null, verificando que usa el camino exacto para los vinculados.
- Android: test de Room migration (incremento de versión con la columna nueva).
- Verificación manual end-to-end: escanear un ticket real, confirmar con al menos un vínculo manual, abrir "Comparar precios" y verificar que el total estimado por supermercado tiene sentido.
