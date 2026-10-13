package com.aicompanion.ai.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters of an interactive house order ("constrúyeme una casa").
 * Every field may be unset (null) while the dialogue is still asking questions.
 * The spec is encoded into the blueprint id ("house:k=v,...") so that BuildTask
 * persists and regenerates the exact same structure after a world reload.
 */
public class HouseSpec {

    public enum Material {
        WOOD("madera"), STONE("piedra");
        public final String spanish;
        Material(String spanish) { this.spanish = spanish; }
    }

    public static final int MIN_SIZE = 5;
    public static final int MAX_SIZE = 11;
    public static final int MAX_FLOORS = 3;

    public Material mainMaterial;      // answer to "¿de qué material?"
    public Material wallMaterial;
    public Material roofMaterial;
    public Material floorMaterial;
    public String wood;                // WoodType enum name (OAK, BIRCH...) - kept as text so it is testable without bootstrap
    public Integer width;              // X (odd, 5..11)
    public Integer depth;              // Z (odd, 5..11)
    public Integer floors;             // 1..3
    public Boolean windows;
    public Boolean door;
    public Direction facing;           // direction the front door looks at
    public BlockPos origin;            // centre of the ground floor

    // --------------------------------------------------------------------------------
    // Derived values
    // --------------------------------------------------------------------------------

    public Material walls() { return wallMaterial != null ? wallMaterial : (mainMaterial != null ? mainMaterial : Material.WOOD); }
    public Material roof()  { return roofMaterial != null ? roofMaterial : (mainMaterial != null ? mainMaterial : Material.WOOD); }
    public Material floor() { return floorMaterial != null ? floorMaterial : Material.STONE; }
    public String woodOrDefault() { return wood != null ? wood : "OAK"; }
    public int widthOrDefault() { return width != null ? width : 5; }
    public int depthOrDefault() { return depth != null ? depth : 5; }
    public int floorsOrDefault() { return floors != null ? floors : 1; }
    public boolean windowsOrDefault() { return windows == null || windows; }
    public boolean doorOrDefault() { return door == null || door; }
    public Direction facingOrDefault() { return facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH; }

    /** Wood is needed whenever any element is wooden, a door or the corner logs are used. */
    public boolean usesWood() {
        return walls() == Material.WOOD || roof() == Material.WOOD || floor() == Material.WOOD || doorOrDefault();
    }

    public static int normalizeSize(int s) {
        int v = Math.max(MIN_SIZE, Math.min(MAX_SIZE, s));
        if (v % 2 == 0) v += 1; // odd sizes keep the door centred
        return Math.min(v, MAX_SIZE);
    }

    // --------------------------------------------------------------------------------
    // Encoding (persisted inside the BuildTask blueprint name)
    // --------------------------------------------------------------------------------

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("w", String.valueOf(widthOrDefault()));
        m.put("d", String.valueOf(depthOrDefault()));
        m.put("f", String.valueOf(floorsOrDefault()));
        m.put("wall", walls().name());
        m.put("roof", roof().name());
        m.put("floor", floor().name());
        m.put("wood", woodOrDefault());
        m.put("win", windowsOrDefault() ? "1" : "0");
        m.put("door", doorOrDefault() ? "1" : "0");
        m.put("face", facingOrDefault().getName());
        StringBuilder sb = new StringBuilder("house:");
        boolean first = true;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    public static boolean isEncoded(String id) {
        return id != null && id.toLowerCase().startsWith("house:") && id.contains("=");
    }

    public static HouseSpec decode(String id) {
        HouseSpec s = new HouseSpec();
        if (!isEncoded(id)) return s;
        String body = id.substring(id.indexOf(':') + 1);
        for (String part : body.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim().toLowerCase();
            String v = kv[1].trim();
            try {
                switch (k) {
                    case "w" -> s.width = normalizeSize(Integer.parseInt(v));
                    case "d" -> s.depth = normalizeSize(Integer.parseInt(v));
                    case "f" -> s.floors = Math.max(1, Math.min(MAX_FLOORS, Integer.parseInt(v)));
                    case "wall" -> s.wallMaterial = Material.valueOf(v.toUpperCase());
                    case "roof" -> s.roofMaterial = Material.valueOf(v.toUpperCase());
                    case "floor" -> s.floorMaterial = Material.valueOf(v.toUpperCase());
                    case "wood" -> s.wood = v.toUpperCase();
                    case "win" -> s.windows = v.equals("1");
                    case "door" -> s.door = v.equals("1");
                    case "face" -> {
                        Direction d = Direction.byName(v.toLowerCase());
                        if (d != null && d.getAxis().isHorizontal()) s.facing = d;
                    }
                    default -> { }
                }
            } catch (Exception ignored) {
                // malformed value -> keep default
            }
        }
        s.mainMaterial = s.wallMaterial;
        return s;
    }

    public HouseSpec copy() {
        return decodeFull(encode(), this);
    }

    private static HouseSpec decodeFull(String enc, HouseSpec src) {
        HouseSpec c = decode(enc);
        c.mainMaterial = src.mainMaterial;
        c.wallMaterial = src.wallMaterial;
        c.roofMaterial = src.roofMaterial;
        c.floorMaterial = src.floorMaterial;
        c.wood = src.wood;
        c.width = src.width;
        c.depth = src.depth;
        c.floors = src.floors;
        c.windows = src.windows;
        c.door = src.door;
        c.facing = src.facing;
        c.origin = src.origin;
        return c;
    }

    public static String facingSpanish(Direction d) {
        if (d == null) return "norte";
        return switch (d) {
            case SOUTH -> "sur";
            case EAST -> "este";
            case WEST -> "oeste";
            default -> "norte";
        };
    }
}
