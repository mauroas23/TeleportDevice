# Revisión de Teleport Device 1.0.0 — cambios respecto al zip recibido

## Errores y riesgos encontrados (y corregidos)

1. **Teletransporte dentro de bloques a destinos lejanos** (`TeleportService`). Se comprobaban las colisiones
   *antes* de cargar el chunk de destino; en 1.20.1 `noCollision` trata los chunks sin cargar como aire, así que un
   destino lejano podía aprobarse estando dentro de bloques. Ahora los chunks se cargan antes de comprobar.
   *(Detectado leyendo el código y conociendo la API; no se pudo reproducir ejecutando Minecraft.)*
2. **Coordenadas y rotación sin validar**: ahora se rechazan valores no finitos o fuera de ±29 000 000, y la
   rotación se sanea (finita, cabeceo entre −90 y 90).
3. **Excepciones en `teleportTo`** (otros mods o plugins): se capturan, se registran y no se cobra.
4. **HUD con tamaño fijo (340×236)**: se salía de pantalla con ciertas escalas de GUI o ventanas pequeñas. La
   geometría se calcula ahora según el tamaño de pantalla (`GuiLayout`) y la lista absorbe el espacio sobrante.
5. **Texto superpuesto o cortado en el HUD**: el aviso de «viaje entre dimensiones desactivado» se pisaba con las
   coordenadas y con el botón, y se recortaba sin indicarlo. Ahora aparece en la línea de estado; los textos largos
   se recortan con «...».
6. **Caja de texto sin teclado tras «Renombrar»**: parecía enfocada pero no recibía teclas (el clic en el botón se
   llevaba el foco de la pantalla). El foco se devuelve tras el clic.
7. **Limpieza de la caja dependiente de una bandera frágil** (`pendingClear`): si el servidor ignoraba una acción
   por anti-spam, la bandera quedaba armada y vaciaba la caja con una respuesta ajena. Ahora depende de la respuesta
   concreta (guardada / renombrada).
8. **Usabilidad**: la ubicación recién creada queda seleccionada y visible; navegación con ↑ ↓ RePág AvPág Inicio
   Fin; la tecla de inventario cierra el HUD (si no se está escribiendo).
9. **Scroll**: lógica extraída a `ScrollModel` y probada (0–100 filas, arrastre, rueda, 20 000 operaciones aleatorias).
10. **Nombres**: saneado más estricto (caracteres de formato Unicode como el override RTL o los de ancho cero) y
    comprobación de duplicados dentro de la lista del propio jugador (`LocationStorage#nameTaken`).
11. **Red**: contador de ubicaciones validado al decodificar `SyncPacket` (antes un valor negativo provocaba una
    excepción inesperada); `LocationDto` recorta datos corruptos para que nunca rompan el paquete.
12. **Errores inesperados al procesar una acción**: se registran y el jugador recibe `internal_error` en lugar de
    dejar el HUD esperando.
13. **Estado estático del anti-spam**: se limpia al parar el servidor (`ServerStoppedEvent`).
14. **Idiomas**: añadidos es_cl, es_ec, es_uy y es_ve; nueva clave `teleportdevice.msg.internal_error`.
15. **Build**: el proyecto no traía `gradlew` ni el JAR del wrapper (binario imposible de generar sin red). Está
    documentado y se añadió `.github/workflows/build.yml` para compilar en la nube con JDK 17 y Gradle 8.8.

## Lo que ya estaba bien y se conservó

Arquitectura servidor-autoritativa (el cliente solo envía intenciones y nunca coordenadas), canal `SimpleChannel`
con `consumerMainThread`, `SavedData` indexado por UUID, cooldown persistente, coste opcional, lista negra de
dimensiones, receta, logro, modelo, textura y configuración de Gradle (Forge 47.4.10, Java 17, mapeos oficiales,
salida `teleportdevice-1.0.0.jar`).

## Pruebas

Ver «Estado de verificación» en `README.md`. Resumen: sintaxis y referencias internas sin errores; 563 pruebas
unitarias y 140 de integración (con buffer y NBT simulados) sin fallos; recursos y metadatos válidos. **No** se
compiló contra Forge/Minecraft, no se ejecutó el juego y no se probó en Arclight.
