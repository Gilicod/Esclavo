# Esclavo — Compañero Autónomo para Minecraft 1.20.1 (Forge)

**Esclavo** es un mod para **Minecraft Java Edition 1.20.1 (Forge)** que añade un compañero NPC totalmente autónomo con inteligencia híbrida (planificación GOAP + navegación 3D avanzada). Actúa como un segundo jugador en supervivencia capaz de minar, talar árboles, combatir, recolectar recursos, fundir minerales, fabricar herramientas y construir estructuras completas o schematics (`.schem` / `.nbt`) bloque por bloque.

---

## Descarga

Puedes descargar la última versión lista para jugar desde la sección oficial de Releases:

- **[Descargar última versión (esclavo-1.13.65.jar)](https://github.com/Gilicod/Esclavo/releases/latest)**

---

## Ficha Técnica y Requisitos

- **Nombre del Mod:** Esclavo
- **Mod ID:** `aicompanion`
- **Versión Actual:** `1.13.65`
- **Minecraft:** `1.20.1`
- **Mod Loader:** `Forge 47.3.0` o superior
- **Java:** `17`
- **Archivo JAR:** `esclavo-1.13.65.jar`

---

## Instalación

1. Instala **Minecraft Forge 1.20.1** (`47.3.0` o superior).
2. Descarga el archivo `esclavo-1.13.65.jar` desde [Releases](https://github.com/Gilicod/Esclavo/releases/latest).
3. Coloca el archivo `.jar` dentro de la carpeta `mods/` de tu cliente de Minecraft (y en la carpeta `mods/` del servidor si juegas en multijugador).
4. Inicia el juego.

---

## Características Principales

### 1. Estudio de Construcción 3D y Schematics (100 % Supervivencia)
- **Visor 3D Interactivo:** Previsualiza estructuras integradas o tus propios archivos `.schem` y `.nbt` (colocados en `config/esclavo/schematics/`) con rotación (`0°`, `90°`, `180°`, `270°`), zoom y corte por capas.
- **Modos de Materiales:** Construye respetando los bloques originales del plano (`ORIGINAL`), unificando con un solo material (`SINGLE_MATERIAL`) o en estructura hueca (`SINGLE_MATERIAL_HOLLOW`).
- **Construcción Real en Supervivencia:** Esclavo nivela el terreno usando la herramienta adecuada, recolecta o fabrica los materiales faltantes (tablones, escaleras, losas, puertas, camas, cristal, antorchas), coloca andamios temporales seguros y los retira al finalizar.
- **Persistencia de Proyecto (`BuildProject`):** Conserva el ancla exacta, orientación y progreso de la obra incluso tras desconexiones, reinicios del servidor o reapariciones.

### 2. Minería, Tala, Fundición y Fabricación Autónoma (GOAP)
- **Minería Inteligente (`MineTask`):** Búsqueda espacial de vetas, memoria de depósitos de minerales, iluminación dinámica de túneles y selección automática de picos según el nivel del bloque.
- **Tala Completa (`ChopTask`):** Tala árboles de abajo hacia arriba, alcanza copas altas mediante pilares temporales seguros de tierra/adoquín y limpia cada bloque temporal al descender.
- **Fundición y Crafteo (`SmeltTask` / `CraftTask`):** Fabrica mesas de trabajo, hornos, picos, hachas, palas, antorchas y materiales de construcción automáticamente cuando una misión lo requiere.

### 3. Combate, Supervivencia y Gestión de Inventario
- **Combate y Defensa (`FightTask` / `KillTask`):** Equipa automáticamente la mejor armadura, espada y escudo disponibles; se alimenta y regenera salud tras los combates.
- **Economía de Inventario:** Compacta stacks, deposita excedentes en cofres autorizados (protegiendo siempre sus herramientas, armadura, comida y materiales de la misión activa) y evita llenar su mochila con bloques basura.
- **HUD y Brújula en Pantalla:** Muestra el estado actual, salud, progreso de la misión y dirección hacia Esclavo en todo momento.

---

## Invocación, Controles y Comandos

### Controles y Silbato
- **Tecla `J`** *(configurable)*: Abre el menú principal de **Esclavo** (`INVENTORY`, `NAME`, `CURRENT`, `ORDERS`, `QUEUE`, `AI`, `BUILDING`, `MEMORY`, `STATS`, `CONFIG`).
- **Tecla `H`** *(configurable)*: Llamado rápido (`Recall`) para traer a Esclavo a una posición segura junto a ti.
- **Silbato de Esclavo (`Companion Whistle`):**
  - Se fabrica con 4 lingotes de hierro y 1 de redstone.
  - **Clic derecho:** Invoca a Esclavo o abre su interfaz.
  - **Shift + Clic derecho sobre un cofre:** Autoriza o desautoriza ese cofre para que Esclavo guarde o tome materiales.

### Comandos Principales
- `/companion summon` — Invoca a Esclavo a tu lado.
- `/companion follow` — Ordena a Esclavo seguirte.
- `/companion stay` — Ordena a Esclavo esperar en su posición actual.
- `/companion recall` — Teletransporta a Esclavo a un lugar seguro junto a ti.
- `/companion mine <recurso> <cantidad>` — Ejemplo: `/companion mine iron 16`
- `/companion chop <madera> <cantidad>` — Ejemplo: `/companion chop oak 32`
- `/companion kill <entidad> <cantidad>` — Ejemplo: `/companion kill zombie 10`
- `/companion build <plano>` — Ejemplo: `/companion build small_house`
- `/companion order "<orden en lenguaje natural>"` — Ejemplo: `/companion order "mina 20 de carbón y construye una casa"`
