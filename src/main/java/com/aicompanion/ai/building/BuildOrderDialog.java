package com.aicompanion.ai.building;

import net.minecraft.core.Direction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text side of the interactive house dialogue. Pure logic (no world access) so it can be unit tested.
 *
 * The companion only asks for the parameters that were not given in the order:
 * material -> wood species (only if something is wooden) -> size -> floors -> windows -> door ->
 * location -> facing -> summary/confirmation. "como quieras" / "tú decide" accepts the default.
 */
public class BuildOrderDialog {

    public enum Step { MATERIAL, WOOD, SIZE, FLOORS, WINDOWS, DOOR, LOCATION, FACING, CONFIRM }

    public enum LocationMode { NONE, HERE_PLAYER, HERE_COMPANION, COORDS }
    public enum FacingMode { NONE, EXPLICIT, TOWARDS_PLAYER, PLAYER_LOOK }

    private final HouseSpec spec = new HouseSpec();
    private Step awaiting = null;
    private LocationMode locationMode = LocationMode.NONE;
    private int[] coords = null;
    private FacingMode facingMode = FacingMode.NONE;
    private long lastActivity = 0;

    public HouseSpec getSpec() { return spec; }
    public Step getAwaiting() { return awaiting; }
    public void setAwaiting(Step s) { this.awaiting = s; }
    public LocationMode getLocationMode() { return locationMode; }
    public int[] getCoords() { return coords; }
    public FacingMode getFacingMode() { return facingMode; }
    public long getLastActivity() { return lastActivity; }
    public void touch(long gameTime) { this.lastActivity = gameTime; }

    // ------------------------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------------------------

    /** "constrúyeme una casa", "haz una casa de madera"... (refugio/torre/puente/granja keep the old flow). */
    public static boolean isHouseRequest(String t) {
        if (t == null) return false;
        boolean house = t.matches(".*\\b(?:casa|casita|cabana|vivienda|hogar)\\b.*");
        boolean verb = t.matches(".*\\b(?:construye|construyeme|construir|construyas|haz|hazme|hacer|crea|creame|levanta|edifica|build)\\b.*");
        boolean returnHome = t.matches(".*\\b(?:vuelve|regresa|volver|ve|ir|ven)\\b.*\\b(?:a\\s+)?(?:la\\s+)?casa\\b.*") && !verb;
        return house && verb && !returnHome;
    }

    public static boolean isCancel(String t) {
        return t != null && t.matches(".*\\b(?:cancela|cancelar|cancelalo|olvidalo|olvida|no\\s+construyas|deja\\s+eso|anula|anular)\\b.*");
    }

    public static boolean isConfirm(String t) {
        return t != null && t.matches("^\\s*(?:si|confirmo|confirmar|confirma|dale|ok|okay|vale|adelante|empieza|comienza|hazlo|de\\s+acuerdo|perfecto|listo)\\b.*");
    }

    public static boolean isModify(String t) {
        return t != null && t.matches(".*\\b(?:modifica|modificar|cambia|cambiar|cambiale|corrige)\\b.*");
    }

    private static boolean isWhatever(String t) {
        return t.matches(".*\\b(?:como\\s+quieras|tu\\s+decide|tu\\s+elige|lo\\s+que\\s+sea|da\\s+igual|cualquiera|me\\s+da\\s+igual|por\\s+defecto|normal)\\b.*");
    }

    // ------------------------------------------------------------------------------------
    // Absorbing answers
    // ------------------------------------------------------------------------------------

    /**
     * Extracts every parameter present in {@code t} (accent-folded, lower case).
     * {@code context} is the question that was just asked, used for short answers ("sí", "2", "como quieras").
     * @return true if at least one parameter was recognised.
     */
    public boolean absorb(String t, Step context) {
        if (t == null) return false;
        t = t.toLowerCase().trim();
        boolean any = false;

        // --- Specific element materials ("paredes de piedra", "techo de madera", "piso de piedra")
        HouseSpec.Material m;
        if ((m = materialAfter(t, "(?:paredes|pared|muros|muro)")) != null) { spec.wallMaterial = m; any = true; }
        if ((m = materialAfter(t, "(?:techo|tejado)")) != null) { spec.roofMaterial = m; any = true; }
        if ((m = materialAfter(t, "(?:piso|suelo)")) != null) { spec.floorMaterial = m; any = true; }

        // --- Wood species (implies wood if no material was given)
        String wood = parseWood(t);
        if (wood != null) {
            spec.wood = wood;
            any = true;
            if (spec.mainMaterial == null && spec.wallMaterial == null) spec.mainMaterial = HouseSpec.Material.WOOD;
        }

        // --- Main material
        HouseSpec.Material main = parseMaterial(t);
        if (main != null && !t.matches(".*\\b(?:paredes|pared|muros|muro|techo|tejado|piso|suelo)\\s+de\\b.*")) {
            spec.mainMaterial = main;
            any = true;
        } else if (main != null && spec.mainMaterial == null && context == Step.MATERIAL) {
            spec.mainMaterial = main;
            any = true;
        }

        // --- Size
        Matcher dims = Pattern.compile("\\b(\\d{1,2})\\s*(?:x|por)\\s*(\\d{1,2})\\b").matcher(t);
        if (dims.find()) {
            spec.width = HouseSpec.normalizeSize(Integer.parseInt(dims.group(1)));
            spec.depth = HouseSpec.normalizeSize(Integer.parseInt(dims.group(2)));
            any = true;
        } else if (t.matches(".*\\b(?:muy\\s+grande|enorme|gigante)\\b.*")) {
            spec.width = 11; spec.depth = 11; any = true;
        } else if (t.matches(".*\\b(?:grande|amplia)\\b.*")) {
            spec.width = 9; spec.depth = 9; any = true;
        } else if (t.matches(".*\\b(?:mediana|normalita|media)\\b.*")) {
            spec.width = 7; spec.depth = 7; any = true;
        } else if (t.matches(".*\\b(?:pequena|pequenita|chica|chiquita|pequeno)\\b.*")) {
            spec.width = 5; spec.depth = 5; any = true;
        } else if (context == Step.SIZE && !t.matches(".*\\b(?:pisos?|plantas?|niveles?)\\b.*")) {
            Integer n = firstNumber(t);
            if (n != null) { spec.width = HouseSpec.normalizeSize(n); spec.depth = spec.width; any = true; }
        }

        // --- Floors
        Matcher fl = Pattern.compile("\\b(\\d|un|una|uno|dos|tres)\\s+(?:pisos?|plantas?|niveles?)\\b").matcher(t);
        if (fl.find()) {
            spec.floors = clampFloors(wordToInt(fl.group(1)));
            any = true;
        } else if (context == Step.FLOORS && !dims.find(0)) {
            Integer n = firstNumber(t);
            if (n != null) { spec.floors = clampFloors(n); any = true; }
        }

        // --- Windows / door
        if (t.matches(".*\\bsin\\s+ventanas?\\b.*")) { spec.windows = false; any = true; }
        else if (t.matches(".*\\bcon\\s+ventanas?\\b.*")) { spec.windows = true; any = true; }
        if (t.matches(".*\\bsin\\s+puertas?\\b.*")) { spec.door = false; any = true; }
        else if (t.matches(".*\\bcon\\s+puertas?\\b.*")) { spec.door = true; any = true; }
        Boolean yesNo = yesNo(t);
        if (yesNo != null && context == Step.WINDOWS && spec.windows == null) { spec.windows = yesNo; any = true; }
        if (yesNo != null && context == Step.DOOR && spec.door == null) { spec.door = yesNo; any = true; }

        // --- Location
        Matcher c = Pattern.compile("(-?\\d+)\\s*[ ,]\\s*(-?\\d+)\\s*[ ,]\\s*(-?\\d+)").matcher(t);
        if (c.find() && (context == Step.LOCATION || t.matches(".*\\b(?:en|coordenadas|coords?)\\b.*"))) {
            coords = new int[]{Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), Integer.parseInt(c.group(3))};
            locationMode = LocationMode.COORDS;
            spec.origin = null;
            any = true;
        } else if (t.matches(".*\\b(?:donde\\s+estas|donde\\s+tu\\s+estas|junto\\s+a\\s+ti|ahi\\s+donde\\s+estas)\\b.*")) {
            locationMode = LocationMode.HERE_COMPANION; spec.origin = null; any = true;
        } else if (t.matches(".*\\b(?:aqui|aca|donde\\s+estoy|cerca\\s+de\\s+mi|frente\\s+a\\s+mi|delante\\s+de\\s+mi|junto\\s+a\\s+mi)\\b.*")) {
            locationMode = LocationMode.HERE_PLAYER; spec.origin = null; any = true;
        }

        // --- Facing
        Direction d = parseDirection(t, context == Step.FACING);
        if (d != null) { spec.facing = d; facingMode = FacingMode.EXPLICIT; any = true; }
        else if (t.matches(".*\\b(?:hacia\\s+mi|mirando\\s+hacia\\s+mi|de\\s+frente\\s+a\\s+mi|que\\s+me\\s+mire)\\b.*")) {
            facingMode = FacingMode.TOWARDS_PLAYER; spec.facing = null; any = true;
        } else if (t.matches(".*\\b(?:hacia\\s+donde\\s+miro|donde\\s+miro|hacia\\s+donde\\s+estoy\\s+mirando)\\b.*")) {
            facingMode = FacingMode.PLAYER_LOOK; spec.facing = null; any = true;
        }

        // --- "como quieras" -> default for the current question
        if (!any && context != null && isWhatever(t)) {
            applyDefault(context);
            any = true;
        }
        return any;
    }

    public void applyDefault(Step s) {
        switch (s) {
            case MATERIAL -> spec.mainMaterial = HouseSpec.Material.WOOD;
            case WOOD -> spec.wood = "OAK";
            case SIZE -> { spec.width = 7; spec.depth = 7; }
            case FLOORS -> spec.floors = 1;
            case WINDOWS -> spec.windows = true;
            case DOOR -> spec.door = true;
            case LOCATION -> locationMode = LocationMode.HERE_PLAYER;
            case FACING -> facingMode = FacingMode.TOWARDS_PLAYER;
            default -> { }
        }
    }

    /** Clears one parameter so it is asked again ("modificar tamaño"). Returns the step to ask, or null. */
    public Step resetFromText(String t) {
        if (t.matches(".*\\b(?:material|materiales)\\b.*")) { spec.mainMaterial = null; spec.wallMaterial = null; spec.roofMaterial = null; spec.floorMaterial = null; return Step.MATERIAL; }
        if (t.matches(".*\\b(?:madera|tipo\\s+de\\s+madera)\\b.*")) { spec.wood = null; return Step.WOOD; }
        if (t.matches(".*\\b(?:tamano|medidas|dimensiones)\\b.*")) { spec.width = null; spec.depth = null; return Step.SIZE; }
        if (t.matches(".*\\b(?:pisos|plantas|niveles|altura)\\b.*")) { spec.floors = null; return Step.FLOORS; }
        if (t.matches(".*\\b(?:ventanas?)\\b.*")) { spec.windows = null; return Step.WINDOWS; }
        if (t.matches(".*\\b(?:puertas?)\\b.*")) { spec.door = null; return Step.DOOR; }
        if (t.matches(".*\\b(?:ubicacion|lugar|sitio|posicion)\\b.*")) { locationMode = LocationMode.NONE; coords = null; spec.origin = null; return Step.LOCATION; }
        if (t.matches(".*\\b(?:orientacion|direccion|frente|fachada)\\b.*")) { facingMode = FacingMode.NONE; spec.facing = null; return Step.FACING; }
        return null;
    }

    public Step nextMissing() {
        if (spec.mainMaterial == null && spec.wallMaterial == null) return Step.MATERIAL;
        boolean woodenParts = spec.walls() == HouseSpec.Material.WOOD || spec.roof() == HouseSpec.Material.WOOD
                || spec.floor() == HouseSpec.Material.WOOD;
        if (woodenParts && spec.wood == null) return Step.WOOD;
        if (spec.width == null || spec.depth == null) return Step.SIZE;
        if (spec.floors == null) return Step.FLOORS;
        if (spec.windows == null) return Step.WINDOWS;
        if (spec.door == null) return Step.DOOR;
        if (locationMode == LocationMode.NONE && spec.origin == null) return Step.LOCATION;
        if (facingMode == FacingMode.NONE && spec.facing == null) return Step.FACING;
        return Step.CONFIRM;
    }

    public static String question(Step s) {
        return switch (s) {
            case MATERIAL -> "¿De qué material quieres la casa? (madera o piedra; también puedes decir p. ej. 'paredes de piedra y techo de madera')";
            case WOOD -> "¿Qué madera uso? (roble, abedul, abeto, roble oscuro, acacia, cerezo, jungla, manglar o bambú)";
            case SIZE -> "¿De qué tamaño? (pequeña 5x5, mediana 7x7, grande 9x9, muy grande 11x11, o medidas como 7x9)";
            case FLOORS -> "¿Cuántos pisos? (1, 2 o 3)";
            case WINDOWS -> "¿Le pongo ventanas de vidrio? (sí / no)";
            case DOOR -> "¿Le pongo puerta? (sí / no)";
            case LOCATION -> "¿Dónde la construyo? ('aquí' = delante de ti, 'donde estás' = donde estoy yo, o coordenadas 'x y z')";
            case FACING -> "¿Hacia dónde miro la puerta? (norte, sur, este, oeste o 'hacia mí')";
            case CONFIRM -> "Responde 'confirmar' para empezar, 'modificar ...' para cambiar algo, o 'cancelar'.";
        };
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    private static HouseSpec.Material materialAfter(String t, String elementRegex) {
        Matcher mm = Pattern.compile("\\b" + elementRegex + "\\s+(?:de|en)\\s+(madera|tablas|tablones|piedra|adoquin|roca|tronco)").matcher(t);
        if (!mm.find()) return null;
        String w = mm.group(1);
        return (w.equals("piedra") || w.equals("adoquin") || w.equals("roca")) ? HouseSpec.Material.STONE : HouseSpec.Material.WOOD;
    }

    private static HouseSpec.Material parseMaterial(String t) {
        if (t.matches(".*\\b(?:piedra|adoquin|roca|de\\s+piedra)\\b.*")) return HouseSpec.Material.STONE;
        if (t.matches(".*\\b(?:madera|tablas|tablones|de\\s+madera)\\b.*")) return HouseSpec.Material.WOOD;
        return null;
    }

    static String parseWood(String t) {
        if (t.matches(".*\\broble\\s+oscuro\\b.*")) return "DARK_OAK";
        if (t.matches(".*\\babedul\\b.*")) return "BIRCH";
        if (t.matches(".*\\b(?:abeto|pino)\\b.*")) return "SPRUCE";
        if (t.matches(".*\\bacacia\\b.*")) return "ACACIA";
        if (t.matches(".*\\bcerezo\\b.*")) return "CHERRY";
        if (t.matches(".*\\bjungla\\b.*")) return "JUNGLE";
        if (t.matches(".*\\b(?:manglar|mangle)\\b.*")) return "MANGROVE";
        if (t.matches(".*\\bbambu\\b.*")) return "BAMBOO";
        if (t.matches(".*\\broble\\b.*")) return "OAK";
        return null;
    }

    static Direction parseDirection(String t) {
        return parseDirection(t, false);
    }

    /**
     * "este" also means "this" in Spanish, so a direction needs a preposition
     * ("al este", "hacia el norte", "mirando al sur") unless we are answering the facing question.
     */
    static Direction parseDirection(String t, boolean answeringFacing) {
        String prefix = answeringFacing ? "(?:^|.*\\b)" : ".*\\b(?:al|hacia\\s+el|hacia|mirando\\s+al|mirando\\s+hacia\\s+el|orientada\\s+al|del\\s+lado)\\s+";
        if (t.matches(prefix + "(?:norte|north)\\b.*")) return Direction.NORTH;
        if (t.matches(prefix + "(?:sur|south)\\b.*")) return Direction.SOUTH;
        if (t.matches(prefix + "(?:este|east|oriente)\\b.*")) return Direction.EAST;
        if (t.matches(prefix + "(?:oeste|west|occidente)\\b.*")) return Direction.WEST;
        return null;
    }

    private static Boolean yesNo(String t) {
        if (t.matches("^\\s*(?:si|claro|por\\s+supuesto|obvio|dale|ok|vale)\\b.*")) return true;
        if (t.matches("^\\s*(?:no|nop|nada|ninguna)\\b.*")) return false;
        return null;
    }

    private static Integer firstNumber(String t) {
        Matcher n = Pattern.compile("\\b(\\d{1,2})\\b").matcher(t);
        if (n.find()) return Integer.parseInt(n.group(1));
        if (t.matches(".*\\b(?:un|uno|una)\\b.*")) return 1;
        if (t.matches(".*\\bdos\\b.*")) return 2;
        if (t.matches(".*\\btres\\b.*")) return 3;
        return null;
    }

    private static int wordToInt(String w) {
        return switch (w) {
            case "un", "una", "uno" -> 1;
            case "dos" -> 2;
            case "tres" -> 3;
            default -> Integer.parseInt(w);
        };
    }

    private static int clampFloors(int n) {
        return Math.max(1, Math.min(HouseSpec.MAX_FLOORS, n));
    }
}
