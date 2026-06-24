# Foto del ticket como registro de la compra + elección manual/IA

## Contexto

Hoy, en `PurchaseDetailScreen`, agregar productos a una compra tiene dos
entradas separadas:

- El FAB "+" → `ProductFormScreen`, carga manual de un producto suelto.
- El botón "Adjuntar ticket" → abre `PhotoSourceChooserDialog` (cámara o
  galería), las fotos se acumulan en `stagedPhotos` (estado transitorio de
  Compose), se muestran en `TicketPhotosPreviewScreen`, y al tocar "Scan" se
  llama de inmediato a `PurchaseDetailViewModel.scanTicketFromUris()`, que
  redimensiona/comprime cada imagen, la manda en base64 al backend
  (`POST /purchases/:id/scan-ticket`) y nunca persiste la foto en ningún
  lado — se descarta después de escanear.

Esto tiene dos problemas:

1. La foto del ticket (el comprobante real de la compra) no queda guardada
   en ningún lado — no hay forma de volver a verla después.
2. Sacar la foto dispara el escaneo con IA inmediatamente, sin que el
   usuario pueda elegir cargar los productos a mano en cambio. El único
   indicador de que algo está pasando es un `CircularProgressIndicator` de
   24dp al lado del botón — fácil de no notar, lo que puede hacer pensar
   que la app se colgó.

## Objetivo

1. Sacar/elegir la foto del ticket pasa a ser un paso independiente: se
   guarda como parte del registro de la compra (visible después, como
   prueba de la compra) ANTES de decidir cómo se cargan los productos.
2. Una vez guardada la foto, el usuario elige entre cargar los productos a
   mano o pedirle ayuda a la IA — y puede cambiar de opinión después en
   cualquier momento, no es una decisión única e irreversible.
3. Mientras la IA está procesando, se muestra una barra de progreso a
   pantalla completa, inconfundible, en vez del spinner chico actual.

## Alcance

Este flujo nuevo **reemplaza** al botón "Adjuntar ticket" actual (mismo
caso de uso: hay un ticket de por medio). El FAB de agregar un producto
suelto sin ticket queda **sin cambios** — sigue siendo una entrada
independiente para ese caso.

No hay cambios de backend: `POST /purchases/:id/scan-ticket` y
`scanTicket()` (`backend/src/lib/ticketScanner.ts`) se usan tal como están
hoy. Todo el trabajo es del lado del cliente Android.

## Diseño

### Modelo de datos

Nueva entidad Room `TicketPhotoEntity`:

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `Long` (PK, autogenerate) | |
| `purchaseId` | `Long` (FK → `PurchaseEntity.id`, `onDelete = CASCADE`) | |
| `filePath` | `String` | ruta absoluta dentro del almacenamiento interno de la app |
| `displayOrder` | `Int` | orden de la foto dentro del ticket (para tickets de varias fotos) |
| `capturedAt` | `Long` (epoch millis) | |

Nuevo `TicketPhotoDao` con:
- `insertAll(photos: List<TicketPhotoEntity>)`
- `getByPurchaseId(purchaseId: Long): Flow<List<TicketPhotoEntity>>`
- `deleteByPurchaseId(purchaseId: Long)` (para el cleanup de archivos al
  borrar una compra — ver más abajo; el cascade de Room borra las filas
  pero no toca el filesystem)

`PurchaseEntity` y `ProductEntity` no cambian.

### Almacenamiento de archivos

Las fotos se guardan en
`context.filesDir/ticket_photos/<purchaseId>/<uuid>.jpg`, usando los
mismos bytes ya redimensionados/comprimidos que hoy se generan para subir
a Gemini (`resizeImageForUpload`) — no hay una segunda compresión ni se
guarda el original sin comprimir. Esto se decidió así para mantener bajo
el uso de espacio en el teléfono; la imagen comprimida alcanza para verla
como referencia visual del ticket.

### Flujo de UI

1. Usuario toca "Adjuntar ticket" (mismo botón de siempre, mismo ícono).
2. `PhotoSourceChooserDialog` (cámara/galería) y `TicketPhotosPreviewScreen`
   (grilla de fotos con sacar más / quitar) — **sin cambios**, se reutilizan
   tal cual.
3. Al confirmar las fotos en `TicketPhotosPreviewScreen` (lo que hoy es el
   botón "Scan"): en vez de escanear de inmediato, las fotos se escriben a
   disco y se insertan las filas `TicketPhotoEntity` correspondientes.
   Recién ahí aparece una pantalla/sheet nueva con dos botones: **"Cargar
   manualmente"** y **"Ayudame con IA"**.
4. "Cargar manualmente" → navega a `ProductFormScreen` (la pantalla manual
   de siempre, sin cambios), tantas veces como productos quiera agregar.
5. "Ayudame con IA" → lee los bytes de los archivos ya persistidos (en vez
   de las URIs transitorias de antes), los manda al backend igual que
   hoy, y mientras espera la respuesta muestra `TicketScanningOverlay`: una
   pantalla completa bloqueante con `LinearProgressIndicator` indeterminado
   (no hay manera de medir % real porque es un solo llamado a Gemini sin
   pasos intermedios) y el texto "Analizando tu ticket con IA, esto puede
   tardar unos segundos...". Al terminar, sigue el flujo existente sin
   cambios: `TicketConfirmScreen` para revisar/editar antes de guardar.
6. Nuevo componente `TicketPhotoStrip`: fila horizontal de miniaturas en la
   parte superior de `PurchaseDetailScreen`, arriba de la lista de
   productos. Tocar una miniatura abre un visor simple a pantalla completa.
   Los dos botones de elección ("Cargar manualmente" / "Ayudame con IA")
   quedan accesibles desde acá también (por ejemplo, debajo de la fila de
   miniaturas), no solo apenas se sacan las fotos — así el usuario puede
   cambiar de método en cualquier momento mientras la compra exista.

### Flujo de datos

```
Capturar/elegir fotos
  → redimensionar/comprimir (lógica existente)
  → escribir bytes a archivo(s) en almacenamiento interno
  → insertar TicketPhotoEntity (uno por foto, con displayOrder)
  → TicketPhotoStrip se actualiza solo (Flow de Room, mismo patrón reactivo
    que ya usa el resto de la app)

"Ayudame con IA"
  → leer bytes de los archivos ya persistidos
  → base64 + POST /purchases/:id/scan-ticket (sin cambios de backend)
  → TicketScanState.Scanning → TicketScanningOverlay
  → TicketConfirmScreen (sin cambios) → guardar productos

"Cargar manualmente"
  → ProductFormScreen (sin cambios), una vez por producto

Borrar una compra
  → cascade de Room borra las filas TicketPhotoEntity
  → el repositorio de compras también borra los archivos del filesystem
    correspondientes a esa compra (paso explícito, el cascade de Room no
    toca el filesystem)
```

### Manejo de errores

- Falla al escribir una foto a disco (ej. sin espacio) → snackbar de error,
  no se crea la fila `TicketPhotoEntity` para esa foto, el usuario puede
  reintentar la captura.
- Falla el escaneo con IA (`GeminiUnavailableError` / `GeminiParseError`,
  mismos casos que hoy) → se cierra `TicketScanningOverlay`, se muestra el
  `TicketScanState.Error` existente. Las fotos guardadas no se tocan: el
  usuario puede reintentar "Ayudame con IA" o pasarse a "Cargar
  manualmente" sin tener que volver a sacar fotos.
- Si la app se cierra o el proceso muere mientras está en `Scanning`: como
  las fotos ya quedaron persistidas antes de llamar a la IA, no se pierde
  nada al reabrir la compra — el usuario simplemente vuelve a tocar
  "Ayudame con IA".

### Testing

- Test de `TicketPhotoDao`: insertar, consultar por `purchaseId`, cascade
  delete al borrar la compra — con el mismo patrón de test (Room en
  memoria) que ya use el proyecto para otros DAOs.
- QA manual en dispositivo/emulador: una foto, ticket largo de varias
  fotos, foto elegida de galería, elegir manual, elegir IA, cambiar de
  manual a IA y viceversa a mitad de camino, borrar una compra y confirmar
  que los archivos realmente se borraron del disco (no solo las filas de
  la base).

## Fuera de alcance

- No se agrega un editor/recorte de la foto antes de guardarla.
- No se agrega forma de borrar una foto individual del registro sin borrar
  toda la compra.
- No cambia nada del backend ni del prompt de Gemini.
