# Klarity — Android App

Aplicación Android para el registro y seguimiento de compras en supermercados.
Mismo nombre y paleta de colores que el proyecto web Klarity.
Trabajo Práctico — Materia: Tecnologías Móviles — Instituto Universitario Aeronautico (IUA)

---

## Estado del proyecto

| Entrega | Fecha límite | Estado |
|---------|-------------|--------|
| Primera entrega (UI / datos mockeados) | 08/05/2026 | ✅ Completa |
| Segunda entrega (funcionalidad real) | TBD | Pendiente |

---

## Descripción

SUPER AHORRO permite al usuario:

- Registrar compras realizadas en distintos supermercados
- Agregar y editar productos dentro de cada compra
- Ver el historial completo de compras con búsqueda y filtros
- Consultar estadísticas de gasto por mes y por supermercado
- Gestionar su perfil y preferencias de la app

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material Design 3 |
| Arquitectura | MVVM |
| Inyección de dependencias | Hilt 2.51.1 |
| Navegación | Navigation Compose 2.8.4 |
| Build | AGP 8.7.3 / Gradle 9.3.1 |
| minSdk | 26 (Android 8.0) |
| compileSdk | 35 |

---

## Estructura del proyecto

```
app/src/main/java/com/undef/superahorroturina/
├── MainActivity.kt
├── SuperAhorroApp.kt
├── model/
│   ├── Models.kt          # Data classes: User, Purchase, Product, StatSummary
│   └── MockData.kt        # Datos mockeados: supermercados argentinos, productos reales
└── ui/
    ├── theme/
    │   ├── Color.kt        # Paleta completa light/dark
    │   ├── Type.kt         # Tipografía Material 3
    │   └── Theme.kt        # Esquemas de color, status bar edge-to-edge
    ├── navigation/
    │   ├── Routes.kt       # Rutas tipadas
    │   └── NavGraph.kt     # Grafo de navegación completo
    ├── components/
    │   └── Components.kt   # Componentes reutilizables
    └── screens/
        ├── splash/         # SplashScreen
        ├── auth/           # LoginScreen, RegisterScreen
        ├── home/           # HomeScreen (Navigation Drawer + Bottom Nav + FAB)
        ├── history/        # HistoryScreen (búsqueda + FilterChips)
        ├── stats/          # StatsScreen (gráfico Canvas + rankings)
        ├── profile/        # ProfileScreen
        ├── settings/       # SettingsScreen (dark mode, idioma)
        ├── purchase/       # NewPurchaseScreen, PurchaseDetailScreen
        └── product/        # ProductFormScreen
```

---

## Pantallas implementadas

| Pantalla | Descripción |
|----------|-------------|
| Splash | Animación Spring + auto-navegación a Login |
| Login | Formulario con validación básica |
| Registro | Nombre, apellido, email, teléfono, contraseña |
| Home | Resumen del mes, últimas compras, Navigation Drawer + Bottom Nav + FAB |
| Historial | Lista completa con búsqueda y FilterChips por supermercado |
| Estadísticas | Gráfico de barras (Canvas), progreso por supermercado, ranking de productos |
| Perfil | Avatar con iniciales, campos editables inline |
| Ajustes | Dark mode toggle, selector de idioma, notificaciones |
| Nueva compra | Formulario con selector de supermercado (ExposedDropdownMenu), fecha y hora |
| Detalle de compra | Lista de productos, total calculado automáticamente, placeholder de ticket |
| Formulario de producto | Nuevo/editar producto, cálculo de subtotal en tiempo real |

---

## Paleta de colores

| Token | Light | Dark |
|-------|-------|------|
| Background | `#F4F6FA` | `#0C0F18` |
| Primary | `#3B82F6` | `#60A5FA` |
| Secondary | `#06B6D4` | `#22D3EE` |
| Error | `#EF4444` | `#F87171` |

Paleta inspirada en el proyecto web Klarity del mismo alumno.

---

## Internacionalización

La app soporta **español** (por defecto) e **inglés**.  
Archivos de recursos:
- `res/values/strings.xml` — español
- `res/values-en/strings.xml` — inglés

---

## Datos mockeados

- 8 compras con productos reales de supermercado argentino
- 9 supermercados: Carrefour, Coto, Disco, Jumbo, La Anónima, Vea, Walmart, Día, Makro
- Precios en ARS con 2 decimales
- Estadísticas mensuales y por supermercado

---

## Cómo compilar

1. Clonar el repositorio
2. Abrir en Android Studio Hedgehog o superior
3. Sync Project with Gradle Files
4. Run en emulador (API 26+) o dispositivo físico

```bash
./gradlew assembleDebug
```

El APK de debug queda en:  
`app/build/outputs/apk/debug/app-debug.apk`

---

## Registro de cambios

| Commit | Descripción |
|--------|-------------|
| `02d9e65` | feat: estructura base completa — 11 pantallas, navegación, tema, datos mockeados, i18n |
| `345fcaa` | fix: agregar useAndroidX y enableJetifier a gradle.properties |
| `c480034` | fix: corregir warnings de deprecación (AutoMirrored icons, menuAnchor, statusBarColor) + README |
| HEAD | feat: renombrar a Klarity, logo vectorial, paleta exacta del proyecto web, diseño premium (shapes, tipografía, KlarityButton, botones en 1 línea) |
