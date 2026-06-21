# Biometría funcional: token protegido criptográficamente

## Contexto

La app ya tiene un flujo de `BiometricPrompt` funcionando en `LoginScreen` (huella/PIN
para reingresar si hay una sesión guardada), con los permisos del manifest y la
dependencia `androidx.biometric` ya declarados. El problema, identificado al revisar el
flujo actual, es más profundo que "falta un toggle": el JWT se guarda en texto plano en
`SessionDataStore` (DataStore Preferences) y **se lee sin restricciones** en cualquier
momento — `LoginViewModel.init()` ya lo lee directamente al abrir la app, sin pasar por
la huella. El `BiometricPrompt` actual solo decide si se navega a Home; no protege el
acceso al token en sí. Aunque el escaneo de huella falle o nunca se muestre, el token
sigue legible.

Esta spec rediseña el almacenamiento del token para que la huella proteja algo real: el
token de larga duración no persiste en texto plano entre reinicios de la app — vive
cifrado con una clave de Android Keystore que exige autenticación biométrica reciente
para usarse, y solo existe en texto plano en memoria durante la sesión activa de la app.

## Decisiones de alcance (confirmadas con el usuario)

- Se prioriza arreglar la seguridad real del almacenamiento del token por sobre
  agregar superficialmente un toggle o cambiar la navegación de Splash.
- El método elegido es el **atado criptográficamente a la huella**
  (`BiometricPrompt.CryptoObject` + clave de Android Keystore con
  `setUserAuthenticationRequired(true)`), no el cifrado-en-reposo simple
  (`EncryptedSharedPreferences`). El sistema operativo debe negarse a descifrar el
  token sin una autenticación biométrica (o PIN del dispositivo) reciente.
- El cifrado de la clave exige autenticación en cada uso (encrypt y decrypt) — esto
  significa que activar la huella por primera vez también requiere un escaneo
  exitoso, lo cual crea naturalmente un momento de consentimiento explícito en el
  login, en vez de activarse solo como hoy.
- Se agrega un toggle en Ajustes para activar/desactivar manualmente, reutilizando la
  misma función de habilitación que el diálogo de consentimiento del login.
- Sesiones viejas (con token en texto plano de antes de este cambio) NO se migran —
  es un proyecto de cursada sin usuarios reales en producción; tras actualizar, se
  requiere un login con contraseña una vez más. No se construye lógica de migración.
- Fuera de alcance: cambiar la navegación de `SplashScreen` para saltear `LoginScreen`
  directamente a Home; políticas de expiración/logout automático del token (no
  relacionadas con biometría); soporte multi-perfil en un dispositivo compartido.

## Arquitectura de almacenamiento

`SessionDataStore` pasa a tener dos niveles:

- **En memoria (no persistido):** el campo `token`, respaldado por un
  `MutableStateFlow` interno dentro de `SessionDataStore`. La interfaz pública
  (`session: Flow<SessionData>`, `bearerToken: Flow<String>`) no cambia — todo el
  código existente que ya consume esos flows (cada ViewModel que llama
  `session.bearerToken.first()`) sigue funcionando exactamente igual, solo cambia de
  dónde sale el dato internamente.
- **En DataStore (persistido, como hoy):** los campos no sensibles (`firstName`,
  `lastName`, `email`, `phone`, `userId`) — siguen mostrándose aunque el token todavía
  no se haya desbloqueado (ej. tarjeta "Bienvenido de vuelta, Juan" antes de tocar la
  huella).

Nuevo: `BiometricCryptoManager` (`app/src/main/java/com/undef/superahorroturina/ui/biometric/BiometricCryptoManager.kt`):

- Genera/recupera una clave AES-256/GCM en Android Keystore (alias propio, ej.
  `"klarity_session_key"`), creada con:
  - `setUserAuthenticationRequired(true)`
  - Autenticación biométrica fuerte o credencial de dispositivo (`AUTH_BIOMETRIC_STRONG
    or AUTH_DEVICE_CREDENTIAL` en API 30+; `setUserAuthenticationValidityDurationSeconds(-1)`
    como equivalente en API 26-29, dado que `minSdk = 26`)
  - `setInvalidatedByBiometricEnrollment(true)` — la clave se invalida automáticamente
    si se agrega o quita una huella del sistema.
- Expone `getEncryptCipher(): Cipher` y `getDecryptCipher(iv: ByteArray): Cipher`
  (ambos requieren que se complete un `BiometricPrompt` con ese `Cipher` como
  `CryptoObject` antes de poder usarse para cifrar/descifrar realmente).
- Persiste el blob cifrado + IV como Base64 en una nueva clave de DataStore (el blob ya
  está cifrado, no hace falta protegerlo con otra capa) junto a un flag
  `biometricEnabled: Boolean`.
- Borra la clave de Keystore + el blob + el flag (`disableBiometricLogin()`), usado
  tanto al desactivar manualmente como al detectar invalidación de la clave o al cerrar
  sesión.

## Flujo de login y desbloqueo

1. **Login con contraseña exitoso:** se guarda el token en memoria (uso inmediato para
   esta sesión de la app) y los datos no sensibles en DataStore, igual que hoy. Si
   `canUseBiometric()` es `true` y todavía no hay biometría activada
   (`biometricEnabled == false`), se muestra un diálogo único: "¿Activar inicio con
   huella?". Si acepta → `BiometricPrompt` con `CryptoObject` en modo cifrado → éxito →
   se guarda el blob cifrado + IV + `biometricEnabled = true`. Si rechaza o cancela, no
   se vuelve a insistir en esa sesión (puede activarlo después desde Ajustes).
2. **Reapertura de la app con biometría activada:** `LoginScreen` detecta
   `biometricEnabled == true` (sin necesidad de descifrar nada para saberlo) y ofrece
   la huella automáticamente, igual que la UX actual (auto-prompt + botón manual de
   huella en la tarjeta "Bienvenido de vuelta"). Éxito → `BiometricPrompt` con
   `CryptoObject` en modo descifrado + el IV guardado → se obtiene el JWT real → se
   coloca en el token en memoria de `SessionDataStore` → navega a Home. Si
   falla/cancela, quedan los campos de email/contraseña como alternativa normal.
3. **Invalidación de clave** (el usuario agregó o borró una huella del sistema desde la
   última vez): el Keystore tira `KeyPermanentlyInvalidatedException` al intentar usar
   la clave. Se detecta, se llama a `disableBiometricLogin()`, se muestra un mensaje
   breve ("Tu configuración de huella cambió, iniciá sesión de nuevo") y se cae al
   login normal con contraseña — camino esperado, no un crash.
4. **Logout manual:** limpia tanto la sesión en memoria como el blob cifrado y la clave
   de Keystore (`disableBiometricLogin()`) — en un dispositivo compartido, cerrar
   sesión apaga también la huella guardada, no deja una sesión "fantasma" reactivable.

## Toggle en Ajustes

`SettingsScreen` gana una fila nueva ("Inicio con huella") con un `Switch`, en una
sección junto a las que ya existen (apariencia, notificaciones). `SettingsViewModel`
expone:
- `biometricEnabled: Boolean` (leído del flag persistido por `BiometricCryptoManager`).
- `biometricAvailable: Boolean` (de `canUseBiometric()`) — si el dispositivo no tiene
  hardware/PIN configurado, el switch aparece deshabilitado con una nota explicativa en
  vez de ocultarse.

- **Apagar el switch:** no necesita huella — llama a `disableBiometricLogin()`
  directamente (borra blob, clave, flag). Próxima apertura de la app, sin oferta de
  biometría.
- **Prender el switch** (si el usuario lo rechazó al loguearse, o se invalidó antes):
  reutiliza la misma función `enableBiometricLogin(activity, currentToken)` del flujo
  de login — como el usuario ya está autenticado dentro de la app y el token está en
  memoria, solo hace falta el `BiometricPrompt` en modo cifrado para guardar el blob.
  Una sola implementación, dos puntos de entrada (diálogo de login y switch de
  Ajustes).

## Manejo de errores y casos borde

- **Sin hardware biométrico ni PIN configurado:** `canUseBiometric()` (ya existente)
  devuelve `false` — no se ofrece nada, ni en login ni en Ajustes (switch deshabilitado
  con texto aclaratorio).
- **Usuario cancela el prompt** (botón atrás, o "usar contraseña"): no es un error —
  vuelve al formulario de email/contraseña sin mensajes de error (igual que el
  comportamiento actual con `ERROR_USER_CANCELED`/`ERROR_NEGATIVE_BUTTON`).
- **Demasiados intentos fallidos** (`ERROR_LOCKOUT`/`ERROR_LOCKOUT_PERMANENT`): se
  muestra el mensaje de error que ya da el sistema vía el callback `onError` existente,
  y cae a contraseña — no hay nada especial que implementar, `BiometricPrompt` ya lo
  gestiona.
- **Sesión vieja con token en texto plano** (de antes de este cambio): no se migra
  (ver "Decisiones de alcance"). Tras actualizar, se requiere login con contraseña una
  vez.

## Archivos afectados

- Nuevo: `app/src/main/java/com/undef/superahorroturina/ui/biometric/BiometricCryptoManager.kt`
- Modificado: `app/src/main/java/com/undef/superahorroturina/ui/biometric/BiometricHelper.kt`
  (el prompt acepta un `CryptoObject`)
- Modificado: `app/src/main/java/com/undef/superahorroturina/data/local/SessionDataStore.kt`
  (token en memoria + nuevas claves para el blob cifrado/IV/flag)
- Modificado: `app/src/main/java/com/undef/superahorroturina/ui/screens/auth/LoginViewModel.kt`
  y `LoginScreen.kt` (diálogo de consentimiento + desbloqueo real vía `CryptoObject`)
- Modificado: `app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsScreen.kt`,
  `SettingsViewModel.kt`, `SettingsUiState.kt` (switch nuevo)

## Testing / verificación

Sin framework de tests automatizados (convención ya establecida en este repo) —
verificación por `./gradlew :app:compileDebugKotlin` más **prueba manual obligatoria en
un dispositivo/emulador con biometría enrolada**, ya que esto no se puede verificar solo
con compilación. El emulador de Android Studio permite simular huella vía
`adb -e emu finger touch 1` una vez que el AVD tiene un sensor de huella configurado y al
menos una huella "enrolada" en sus ajustes de seguridad simulados.

Casos a probar manualmente:
1. Login con contraseña → aceptar el diálogo de activar huella → cerrar la app →
   reabrir → debe ofrecer la huella y, al escanear, entrar directo a Home.
2. Rechazar el diálogo de activar huella → reabrir la app → no debe ofrecer biometría.
3. Activar el switch en Ajustes después de haber rechazado al loguearse → debe pedir un
   escaneo y dejar la huella activa para la próxima apertura.
4. Desactivar el switch en Ajustes → reabrir la app → no debe ofrecer biometría, debe
   pedir contraseña.
5. Cerrar sesión manualmente con biometría activada → reabrir la app → no debe ofrecer
   biometría (logout debe haber limpiado todo).
6. (Si es posible en el dispositivo de prueba) cambiar las huellas enroladas en el
   sistema y reabrir la app → debe detectar la invalidación, mostrar el mensaje y caer a
   login con contraseña, sin crash.
