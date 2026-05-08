// Esta clase es el punto de entrada de la app para Hilt. Necesito @HiltAndroidApp
// para que Hilt genere el componente raíz y pueda inyectar dependencias en toda la app.
// Sin esta anotación, nada de Hilt funciona — me lo acordé después de un crash de 20 minutos.
package com.undef.superahorroturina

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SuperAhorroApp : Application()
