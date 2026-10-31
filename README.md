# PotitoMobs — Minecraft 1.21.1
Plugin Bukkit/Spigot para Arclight, independiente de MartilloHerrero y NetheriteLock.

## Compilar con GitHub
Sube **el contenido** de esta carpeta a un repositorio GitHub (`pom.xml`, `src`, `.github`). En Actions → Compilar PotitoMobs → Artifacts descarga `PotitoMobs-1.21.1`, extrae el ZIP y sube el JAR a `plugins/`. Java 21.

## Comandos (OP)
- `/potitomobs dano 1|2|3|5`
- `/potitomobs vida 1|2|3`
- `/potitomobs velocidad 1|2`
- `/potitomobs estado`
- `/potitomobs reset`
- `/potitomobs recargar`

Ejemplo: `/potitomobs vida 3`, `/potitomobs dano 1`, `/potitomobs velocidad 2`.

## Jefes de mods: limitación importante
La API Bukkit no ofrece un identificador universal de todos los bosses Fabric. Se excluyen automáticamente Ender Dragon, Wither, Warden, Elder Guardian, nombres/tipos con 'boss', entidades con al menos 200 HP de vida base y las etiquetadas `potitomobs_boss`. **Esto NO garantiza excluir todos los bosses de mods.** Antes de usar en producción, añade los nombres/tipos de tus jefes a `plugins/PotitoMobs/config.yml` o usa `/tag <selector> add potitomobs_boss`. Reinicia/ejecuta `/potitomobs recargar`. Un jefe no identificado podría recibir multiplicadores. La detección de ataques especiales de mods tampoco está garantizada.

Los multiplicadores de vida y velocidad afectan a entidades vivas no excluidas, incluidos animales, porque se pidió 'todos los mobs'. Se modifica el valor base del atributo, conservando modificadores externos. La vida actual mantiene su porcentaje al cambiar de multiplicador. El daño de proyectiles usa su tirador si Bukkit lo expone. Los atributos de mods pueden no estar disponibles mediante Bukkit.
