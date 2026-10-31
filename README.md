# Teleport Device — Forge 1.20.1 (47.4.10) · pensado para servidores dedicados y Arclight

Ítem crafteable **Dispositivo de teletransporte**: clic derecho abre un HUD con tus ubicaciones guardadas
(crear con nombre, renombrar, eliminar, ver coordenadas y teletransportarse). Todo se valida en el servidor.

> **Estado:** código revisado a fondo y probado en lo que permite un entorno sin Minecraft (ver
> «Estado de verificación» al final). El JAR **no se compiló** en el entorno de revisión (sin red ni JDK 17).
> La lista de cambios respecto a la versión anterior está en `REVISION.md`.

## Compilar (genera el JAR)

Requisitos: **JDK 17** y conexión a internet (Gradle descarga Forge, Minecraft y mapeos). Usa Gradle **8.x**
(el wrapper pide 8.8). En IntelliJ, el «Gradle JVM» del proyecto debe ser el JDK 17.

**Opción A — local**

1. Descarga el **MDK oficial de Forge 1.20.1-47.4.10** (https://files.minecraftforge.net) y copia de él
   estos 3 archivos a la raíz de este proyecto: `gradlew`, `gradlew.bat` y `gradle/wrapper/gradle-wrapper.jar`
   (este proyecto no los incluye: el JAR del wrapper es un binario que no se puede generar sin red).
   Alternativa: si ya tienes Gradle 8.x instalado, ejecuta `gradle wrapper --gradle-version 8.8` aquí.
2. Ejecuta `./gradlew build` (Windows: `gradlew.bat build`).
3. El JAR queda en `build/libs/teleportdevice-1.0.0.jar` (ya reofuscado, listo para `mods/`).

**Opción B — en la nube, sin configurar nada local**

Sube el proyecto a un repositorio de GitHub. El workflow `.github/workflows/build.yml` compila con JDK 17 y
Gradle 8.8 y deja `teleportdevice-1.0.0.jar` como artefacto en la pestaña *Actions*. *(Workflow no probado.)*

Para probar en desarrollo: `./gradlew runClient` / `./gradlew runServer`.

## Instalación

Copia el JAR a la carpeta `mods/` del **servidor y de cada cliente** (el mod añade un ítem y un canal de red:
ambos lados necesitan la misma versión).

## Configuración — `config/teleportdevice.toml` (se crea al primer arranque)

| Opción | Defecto | Descripción |
|---|---|---|
| `general.maxLocations` | 10 | Máximo de ubicaciones por jugador (1–100) |
| `general.cooldownSeconds` | 30 | Espera entre teletransportes (0 = sin espera) |
| `general.allowCrossDimension` | true | Permitir viajar entre dimensiones |
| `general.dimensionBlacklist` | [] | Dimensiones donde no se puede guardar ni salir/llegar |
| `general.creativeBypass` | true | Creativo ignora cooldown y coste |
| `cost.costItem` | `minecraft:ender_pearl` | Ítem que se consume por viaje |
| `cost.costCount` | 0 | Cantidad por viaje (0 = gratis) |

Reinicia el servidor tras editar el archivo.

## Receta

`src/main/resources/data/teleportdevice/recipes/teleport_device.json` (shaped, editable):

```
O E O      O = obsidiana     E = ojo de ender
P C P      P = perla de ender C = brújula
O D O      D = diamante
```

Para cambiarla sin recompilar: crea un datapack que sobrescriba `data/teleportdevice/recipes/teleport_device.json`.

## El HUD

- La geometría se calcula según el tamaño de pantalla ya escalado: con el alto/ancho disponibles la lista crece o se
  encoge (de ~2,7 a 5 filas) y la columna de botones se mantiene dentro del panel; con pantallas muy bajas se
  omite el bloque de detalles (la lista ya muestra nombre, dimensión y coordenadas).
- Scroll con rueda, arrastrando la barra o con las teclas ↑ ↓ RePág AvPág Inicio Fin. Con muchas ubicaciones la barra
  se mantiene usable (pulgar mínimo). Al crear una ubicación, queda seleccionada y visible.
- `Enter` en la caja de texto guarda (o aplica el renombrado). `Esc` o la tecla de inventario (si no se está
  escribiendo) cierran el HUD. Eliminar pide confirmación (segundo clic).
- La vista nunca decide nada: cada botón envía una petición y el servidor responde con el estado completo.

## Dónde se guardan los datos

`<mundo>/data/teleportdevice_locations.dat` (NBT, servidor). Indexado por UUID del jugador.
También guarda el instante del último teletransporte, así el cooldown no se evade reconectando.

## Seguridad / anti-exploit

- El cliente sólo envía *intenciones* (`ActionPacket`: acción + UUID de ubicación + nombre). **Nunca coordenadas.**
- Al guardar, X/Y/Z/dimensión/rotación salen de la posición real del jugador en el servidor.
- Teletransportar/borrar/renombrar buscan el UUID **dentro de la lista del remitente**; un UUID ajeno => «no existe».
- El remitente lo determina Forge (`ctx.getSender()`); el paquete no puede suplantar a otro jugador.
- Cada acción exige jugador vivo con el dispositivo en mano, aplica anti-spam (4 ticks entre acciones), nombres
  saneados (1–32 caracteres; se eliminan caracteres de control y de formato Unicode y `§`), sin duplicados dentro
  de la lista del jugador, límite de ubicaciones, cooldown y coste.
- Paquetes mal formados: una acción inválida se ignora; cadenas demasiado largas, longitudes declaradas absurdas o
  paquetes truncados provocan un error de decodificación (la conexión se cierra); un contador de ubicaciones
  absurdo en el paquete servidor→cliente también se rechaza. Un fallo inesperado al procesar una acción se registra
  y se avisa al jugador sin tumbar el servidor.
- Destino seguro: los chunks de destino se cargan **antes** de comprobar colisiones; sin colisión, sin
  lava/fuego/cactus/magma/bayas/nieve en polvo/portales, dentro del borde del mundo y de ±29 000 000, coordenadas
  finitas, suelo a ≤3 bloques (evita vacío). Si la posición exacta no es segura se busca alrededor (radio 3, ±6 en Y,
  de dentro hacia fuera) y, en dimensiones sin techo, la superficie. Si no hay nada seguro, no se teletransporta ni
  se cobra.
- El coste y el cooldown sólo se aplican si el teletransporte realmente ocurrió.

## Arclight

Sólo se usan APIs vanilla/Forge (`ServerPlayer#teleportTo`, `SavedData`, `SimpleChannel`). En Arclight un plugin
puede cancelar o desviar un teletransporte: el mod comprueba la dimensión y la posición finales y, si no coinciden
con lo pedido, avisa al jugador y **no cobra ni activa el cooldown**. No requiere ningún plugin.
*No se probó en un servidor Arclight real.*

## Estado de verificación

**Hecho** (sin Minecraft; con javac 21 y arneses propios):
- Revisión manual completa de todas las clases y de cómo se relacionan.
- Sintaxis Java de todo el proyecto y referencias entre clases propias: sin errores (lo único que javac no puede
  resolver sin las librerías de Minecraft/Forge son esas mismas clases).
- 563 pruebas unitarias: reglas de nombres, cooldown, orden de búsqueda de destino, modelo de scroll (0–100 filas,
  20 000 operaciones aleatorias) y geometría del HUD (sin solapamientos ni controles fuera del panel en miles de
  tamaños de pantalla y en 12 resoluciones × escalas de GUI).
- 140 pruebas de integración con un buffer de red y un NBT *simulados*: ida y vuelta de `ActionPacket`,
  `SyncPacket` y `LocationDto`, paquetes mal formados, guardado/carga, crear/renombrar/eliminar y aislamiento entre
  jugadores.
- Validación de JSON, TOML, claves de idioma (8 idiomas), receta, logro, modelo, textura y coherencia
  `gradle.properties` ↔ `build.gradle` ↔ `mods.toml`.

**No hecho** (no hay red ni JDK 17 en el entorno de revisión, ni se puede ejecutar Minecraft):
- Compilar contra Forge/Minecraft reales y generar el JAR; las llamadas a la API de 1.20.1 se revisaron a mano.
- Ejecutar el juego: abrir el HUD con clic derecho, aspecto real, botones y scroll con el ratón.
- Servidor Arclight real con varios jugadores; teletransporte real a miles de bloques y entre dimensiones;
  persistencia tras reiniciar un servidor real.

## Estructura

```
com.teleportdevice
├─ TeleportDeviceMod        entrada, registro, creative tab, config
├─ registry/ModItems
├─ item/TeleportDeviceItem  clic derecho -> pide al servidor abrir la GUI
├─ config/TeleportConfig
├─ data/                    SavedLocation + LocationStorage (SavedData persistente)
├─ network/                 SimpleChannel, ActionPacket (C2S), SyncPacket (S2C), LocationDto
├─ server/                  ServerActions (validación), TeleportService (lógica/seguridad), ServerEvents
├─ util/                    NameRules, Cooldown, SearchOrder, ScrollModel, GuiLayout (puras, sin Minecraft)
└─ client/                  TeleportDeviceScreen (HUD), ClientHooks
```
