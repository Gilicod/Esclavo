package com.aicompanion.ai.parser;

import com.aicompanion.ai.planning.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskParser {

    private static final Pattern ACTION_VERB_AFTER_Y = Pattern.compile(
            "\\s+y\\s+(?=(?:despues|luego|trae|traer|traeme|traemelo|traemelos|traelo|traelos|damelo|damelos|entrega|entregar|entregame|entregamelo|entregamelos|guarda|guardes|guardar|guardalo|guardalos|deposita|deposites|depositar|depositalo|depositalos|conserva|conservar|conservalo|conservalos|quedatelo|quedatelos|quedatela|quedatelas|mantenlo|mantenlos|vuelve|volver|regresa|regresar|ve\\s+a|sigueme|espera|quedate|construye|construir|tala|talar|mina|minar|pica|picar|caza|cazar|mata|matar|consigue|conseguir|busca|busques|buscar)\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern INLINE_DEPOSIT_VERB = Pattern.compile(
            "(?<=\\S)\\s+(?=(?:guarda|guardes|guardar|guardalo|guardalos|deposita|deposites|depositar|depositalo|depositalos)\\s+(?:en|al|a)\\s+(?:el\\s+)?(?:vaul|baul|cofre)\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern INLINE_DELIVERY_PRONOUNS = Pattern.compile(
            "(?<=\\S)\\s+(?=(?:traemelos|traemelo|traelos|traelo|damelo|damelos|entregamelos|entregamelo)\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONNECTOR_SPLIT = Pattern.compile(
            "(?:\\s*(?:y\\s+despues|despues|luego|and\\s+then|then|;|\\.|,)\\s*)",
            Pattern.CASE_INSENSITIVE
    );

    public static List<Goal> parse(String text) {
        List<Goal> goals = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return goals;

        // 1. Normalize whole text with Unicode accent folding first
        String normalized = NormalizedOrderText.foldAccents(text);

        // Strip companion prefixes and conversational filler prefixes (e.g. "a y ", "ve a ", "por favor ", etc.)
        normalized = normalized.replaceAll("^(?:(?:alex|companero|bot|esclab|esclavo|amigo)[,:\\s]+|[a-z]\\s+y\\s+|[a-z]\\s+|ve\\s+a\\s+y\\s+|ve\\s+a\\s+|ve\\s+y\\s+|por\\s+favor\\s+|oye\\s+|porfa\\s+|ey\\s+)+", "").trim();

        // 2. Segment by major connectors and verb clauses
        String withDelimiters = ACTION_VERB_AFTER_Y.matcher(normalized).replaceAll(" ; ");
        withDelimiters = INLINE_DEPOSIT_VERB.matcher(withDelimiters).replaceAll(" ; ");
        withDelimiters = INLINE_DELIVERY_PRONOUNS.matcher(withDelimiters).replaceAll(" ; ");
        String[] rawClauses = CONNECTOR_SPLIT.split(withDelimiters);

        for (String clause : rawClauses) {
            String c = clause.trim();
            if (c.isEmpty()) continue;

            // Check if clause contains compound resources e.g. "mina hierro y oro"
            List<Goal> subGoals = parseCompoundClause(c);
            if (!subGoals.isEmpty()) {
                goals.addAll(subGoals);
            } else {
                Goal g = parseSingle(c);
                if (g != null) goals.add(g);
            }
        }

        return goals;
    }

    private static List<Goal> parseCompoundClause(String clause) {
        List<Goal> list = new ArrayList<>();
        // Detect "mina hierro y oro" or "consigue 10 de carbon y 5 de hierro"
        if (clause.matches(".*\\b(?:mina|minar|pica|picar|consigue|conseguir|busca|buscar)\\b.*\\s+y\\s+.*")) {
            String[] subItems = clause.split("\\s+y\\s+");
            if (subItems.length >= 2) {
                // If the second part has no action verb, it's a compound resource list!
                String part2 = subItems[1].trim();
                if (!hasAnyActionVerb(part2)) {
                    Goal g1 = parseSingle(subItems[0].trim());
                    if (g1 != null) {
                        list.add(g1);
                        // Parse second resource inheriting the action
                        int amount2 = extractAmount(part2, 10);
                        ResourceLocation res2 = extractResource(part2);
                        if (res2 != null) {
                            list.add(Goal.obtain(res2, amount2));
                        }
                    }
                }
            }
        }
        return list;
    }

    private static boolean hasAnyActionVerb(String s) {
        return s.matches(".*\\b(?:trae|traer|traeme|traemelo|traemelos|traelo|traelos|dame|damelo|damelos|entrega|entregar|entregame|entregamelo|entregamelos|guarda|guardar|guardalo|guardalos|deposita|depositar|depositalo|depositalos|conserva|conservar|conservalo|conservalos|quedatelo|quedatelos|quedatela|quedatelas|mantenlo|mantenlos|vuelve|regresa|ve|ven|sigueme|espera|quedate|construye|tala|mina|caza|mata)\\b.*");
    }

    private static Goal parseSingle(String s) {
        if (s == null || s.trim().isEmpty()) return null;

        // 1. Follow & Stay
        if (s.matches(".*\\b(?:sigueme|follow\\s*me|seguir|acompaname|ven\\s+conmigo)\\b.*") && !s.contains("consigueme")) {
            return new Goal(Goal.Type.FOLLOW);
        }
        if (s.matches(".*\\b(?:quedate|espera|stay|wait|parate|detente|quieto)\\b.*")) {
            return new Goal(Goal.Type.STAY);
        }

        // 2. Return Home or Return to Owner / Delivery
        if (s.matches(".*\\b(?:vuelve|regresa|volver|ir|return)\\b.*(?:casa|base).*") || s.matches(".*\\b(?:a\\s+casa|a\\s+la\\s+base|a\\s+base)\\b.*")) {
            return new Goal(Goal.Type.RETURN_HOME);
        }

        // Pronouns and phrases for returning to owner / delivering resources:
        // "tráemelos", "tráemelo", "traelos", "traelo", "traeme", "dámelo", "dámelos", "entregamelos", "traer a mí", "conmigo"
        if (s.matches(".*\\b(?:traemelos|traemelo|traelos|traelo|traeme|traer|trae|damelo|damelos|dame|entregamelos|entregamelo|entregame|entrega|entregar|vuelve|regresa|volver|ven\\s+aqui|ven|a\\s+mi|conmigo)\\b.*")
                && !hasResourceKeyword(s)) {
            boolean deliver = s.matches(".*\\b(?:traemelos|traemelo|traelos|traelo|traeme|traer|trae|damelo|damelos|dame|entregamelos|entregamelo|entregame|entrega|entregar)\\b.*");
            return Goal.returnOwner(deliver);
        }

        // 3. Deposit / Chest / Trunk (vaul, baul, cofre, etc.)
        if (s.matches(".*\\b(?:guardalos|guardalo|guarda|guardes|guardar|depositalos|depositalo|deposita|deposites|depositar|almacenalos|almacenalo|almacena|almacenar|baul|vaul|cofre|meter\\s+en\\s+baul|meter\\s+al\\s+baul|meter\\s+en\\s+cofre)\\b.*")) {
            return new Goal(Goal.Type.DEPOSIT);
        }

        // 3b. Keep in inventory
        if (s.matches(".*\\b(?:en\\s+inventario|conservar|conserva|conservalos|quedatela|quedatelos|mantenla|mantenlos)\\b.*")) {
            return new Goal(Goal.Type.STAY);
        }

        // 3c. Permanent Farm / Guard / Maintain chest tasks
        if (s.matches(".*\\b(?:cosecha|cosechar|cultiva|cultivar|cuida(?:r)?\\s+(?:la\\s+)?granja|atender\\s+granja|cuidar\\s+cultivos|farm|huerto)\\b.*")) {
            return Goal.farm(16);
        }
        if (s.matches(".*\\b(?:vigila|vigilar|patrulla|patrullar|protege|proteger|defiende|defender|guardia|centinela)\\b.*")) {
            return Goal.guard(16);
        }
        if (s.matches(".*\\b(?:mantener|manten|abastece|abastecer|organiza|organizar)\\s+(?:el\\s+)?(?:cofre|baul|almacen)\\b.*")) {
            return Goal.maintainChest(null);
        }

        // 4. Coordinates: "ve a 100 64 -200"
        Matcher coordMatch = Pattern.compile("(?:ve\\s+a|go\\s+to|viaja\\s+a)?\\s*(-?\\d+)[,\\s]+(-?\\d+)(?:[,\\s]+(-?\\d+))?").matcher(s);
        if (coordMatch.find()) {
            int x = Integer.parseInt(coordMatch.group(1));
            int y = coordMatch.group(3) != null ? Integer.parseInt(coordMatch.group(2)) : 0;
            int z = coordMatch.group(3) != null ? Integer.parseInt(coordMatch.group(3)) : Integer.parseInt(coordMatch.group(2));
            return Goal.goTo(new BlockPos(x, y, z));
        }

        // 5. Extract amount
        int amount = extractAmount(s, 1);

        // 5b. 1.13.7: id exacto del registro elegido en el desplegable (vanilla o mods), p. ej. "create:zinc_ore"
        Matcher idMatch = Pattern.compile("\\b([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)\\b").matcher(s);
        while (idMatch.find()) {
            String rawId = idMatch.group(1);
            ResourceLocation exact = ResourceLocation.tryParse(rawId);
            if (exact != null && (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(exact)
                    || net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(exact))) {
                boolean mineVerb = s.matches("^(?:mina|minar|pica|picar|excava|excavar|rompe|romper)\\b.*");
                return mineVerb ? Goal.obtain(exact, amount, "mine") : Goal.obtain(exact, amount);
            }
        }

        // 6. Mine / Obtains / Minerals (Compound phrases ordered before single-word substrings)
        for (String key : new String[]{
                "escombros ancestrales", "ancient debris", "ancient_debris",
                "polvo de piedra luminosa", "polvo de glowstone", "polvo luminoso", "polvo piedra luminosa",
                "piedra luminosa", "glowstone",
                "arena de almas", "tierra de almas", "soul sand", "soul_sand",
                "piedra negra", "piedra_negra", "blackstone",
                "cuarzo del nether", "nether quartz", "cuarzo", "cuarzos", "quartz",
                "pizarra profunda", "deepslate",
                "piedra del nether", "netherrack",
                "basalto", "basalt",
                "obsidiana", "obsidian",
                "diamante", "diamantes", "oro", "hierro", "carbon", "cobre", "redstone",
                "esmeralda", "esmeraldas", "lapis", "lapislazuli", "netherita", "netherite",
                "piedra", "adoquin", "adoquín", "cobblestone", "arena", "sand"
        }) {
            if (s.contains(key)) {
                ResourceLocation ore = SpanishAliases.resolveOre(key);
                if (ore != null) {
                    if (amount == 1 && !s.matches(".*\\b1\\b.*")) {
                        amount = 10; // sensible default
                    }
                    return Goal.obtain(ore, amount);
                }
            }
        }

        // 7. Chop / Wood / Trees
        for (String w : new String[]{"roble oscuro", "roble_oscuro", "roble", "abedul", "abeto", "pino", "jungla", "acacia", "manglar", "mangle", "cerezo", "bambu", "carmesi", "distorsionado", "madera", "arbol", "arboles", "árbol", "árboles", "tronco", "troncos", "leña"}) {
            if (s.contains(w)) {
                String wood = SpanishAliases.resolveWood(w);
                if (amount == 1 && !s.matches(".*\\b1\\b.*")) {
                    amount = 16; // sensible default
                }
                String logId = "any".equals(wood) ? "oak_log" : wood + "_log";
                return Goal.obtain(new ResourceLocation("minecraft", logId), amount, wood);
            }
        }

        // 7b. Kill mob for specific drop item: "mata cochino para tosino", "mata cerdos para tocino", "caza vacas para cuero"
        Matcher killForDrop = Pattern.compile(".*\\b(?:mata|matar|caza|cazar|elimina|eliminar)\\s+(?:un\\s+|unos\\s+|\\d+\\s+)?([a-z]+)\\s+para\\s+(?:conseguir\\s+|obtener\\s+)?([a-z_\\s]+)").matcher(s);
        if (killForDrop.find()) {
            String mobPart = killForDrop.group(1).trim();
            String itemPart = killForDrop.group(2).trim();
            ResourceLocation itemRes = ItemSearchRegistry.resolve(itemPart);
            ResourceLocation mobRes = SpanishAliases.resolveMob(mobPart);
            if (itemRes != null) {
                return Goal.obtain(itemRes, amount);
            } else if (mobRes != null) {
                return Goal.kill(mobRes, amount);
            }
        }

        // 8. Kill / Mob hunt
        for (String m : new String[]{"creeper", "creepers", "zombie", "zombies", "esqueleto", "esqueletos", "arana", "aranas", "enderman", "bruja", "calamar", "vaca", "vacas", "cerdo", "cerdos", "cochino", "cochinos", "puerco", "puercos", "chancho", "chanchos", "marrano", "marranos", "oveja", "ovejas", "gallina", "gallinas", "pollo", "pollos"}) {
            if (s.contains(m)) {
                ResourceLocation mob = SpanishAliases.resolveMob(m);
                if (mob != null) {
                    if (amount == 1 && !s.matches(".*\\b1\\b.*")) {
                        amount = 5;
                    }
                    return Goal.kill(mob, amount);
                }
            }
        }

        // 9. Build
        if (s.contains("construye") || s.contains("construir") || s.contains("build") || s.contains("casa") || s.contains("refugio") || s.contains("torre") || s.contains("puente") || s.contains("granja") || s.contains("almacen") || s.contains("bodega") || s.contains("muralla") || s.contains("muro")) {
            // 9a. Check custom schematics from config/esclavo/schematics/
            for (String customId : com.aicompanion.ai.building.BlueprintRegistry.getCustomSchematicIds()) {
                String cleanCustom = customId.toLowerCase().replace("_", " ");
                if (s.contains(customId) || s.contains(cleanCustom)) {
                    return Goal.build(customId);
                }
            }

            String afterVerb = s.replaceAll("^(?:construye|construir|build)\\s+", "").trim();
            if (!afterVerb.isEmpty()) {
                String cleanTarget = afterVerb.replace(" ", "_");
                if (com.aicompanion.ai.building.BlueprintRegistry.get(cleanTarget) != null) {
                    return Goal.build(cleanTarget);
                }
            }

            String wood = "oak";
            for (String w : new String[]{"roble oscuro", "roble_oscuro", "abedul", "abeto", "jungla", "acacia", "cerezo", "manglar", "bambu", "roble"}) {
                if (s.contains(w)) {
                    wood = SpanishAliases.resolveWood(w);
                    break;
                }
            }
            String stone = "cobblestone";
            if (s.contains("ladrillo") || s.contains("ladrillos")) stone = "stone_bricks";
            else if (s.contains("pizarra")) stone = "deepslate";
            else if (s.contains("arenisca")) stone = "sandstone";
            else if (s.contains("piedra") || s.contains("adoquin")) stone = "cobblestone";

            String bpId = "small_house:" + wood + ":" + stone;
            if (s.contains("dos pisos") || s.contains("2 pisos") || s.contains("dos plantas")) bpId = "two_story_house:" + wood + ":" + stone;
            else if (s.contains("almacen") || s.contains("bodega") || s.contains("deposito")) bpId = "warehouse:" + wood + ":" + stone;
            else if (s.contains("muralla") || s.contains("muro") || s.contains("pared defensiva")) bpId = "defensive_wall:" + wood + ":" + stone;
            else if (s.contains("refugio") || s.contains("choza")) bpId = "shelter";
            else if (s.contains("grande") || s.contains("mansion")) bpId = "medium_house:" + wood + ":" + stone;
            else if (s.contains("torre")) bpId = "watchtower";
            else if (s.contains("puente")) bpId = "bridge";
            else if (s.contains("granja")) bpId = "farm";

            return Goal.build(bpId);
        }

        // 10. Universal Item Search from dictionary
        String itemQuery = s.replaceAll("^(?:busca|busques|consigue|consigueme|trae|traeme|traemelo|traemelos|dame|recolecta|recoge|encuentra|mina|tala|corta|caza|mata)\\s+", "")
                .replaceAll("^\\d+\\s*(?:de\\s+|bloques?\\s+de\\s+)?", "")
                .trim();
        ResourceLocation resolved = ItemSearchRegistry.resolve(itemQuery.isEmpty() ? s : itemQuery);
        if (resolved == null && !itemQuery.isEmpty()) {
            resolved = ItemSearchRegistry.resolve(s);
        }
        if (resolved != null) {
            return Goal.obtain(resolved, amount);
        }

        return null;
    }

    private static int extractAmount(String s, int defaultAmount) {
        Matcher stackMatch = Pattern.compile("(\\d+)?\\s*stacks?").matcher(s);
        if (stackMatch.find()) {
            int numStacks = stackMatch.group(1) != null ? Integer.parseInt(stackMatch.group(1)) : 1;
            return numStacks * 64;
        }
        Matcher numMatch = Pattern.compile("\\b(\\d+)\\b").matcher(s);
        if (numMatch.find()) {
            return Integer.parseInt(numMatch.group(1));
        }
        return defaultAmount;
    }

    private static ResourceLocation extractResource(String s) {
        String query = s.replaceAll("^\\d+\\s*(?:de\\s+)?", "").trim();
        ResourceLocation ore = SpanishAliases.resolveOre(query);
        if (ore != null) return ore;
        ResourceLocation mob = SpanishAliases.resolveMob(query);
        if (mob != null) return mob;
        return ItemSearchRegistry.resolve(query);
    }

    public static String formatGoalDescription(Goal goal) {
        if (goal == null) return "Tarea desconocida";
        return switch (goal.getType()) {
            case OBTAIN -> {
                ResourceLocation res = goal.getTargetResource();
                String readable = ItemSearchRegistry.getDisplayName(res);
                String path = res != null ? res.getPath() : "";
                if (path.contains("log") || path.contains("wood")) {
                    yield "Talar " + goal.getAmount() + " de Madera (" + readable + ")";
                } else if (path.contains("ore") || path.contains("stone") || path.contains("deepslate") || path.contains("obsidian") || path.contains("cobble")) {
                    yield "Minar " + goal.getAmount() + " de " + readable;
                } else {
                    yield "Conseguir " + goal.getAmount() + " de " + readable;
                }
            }
            case KILL -> {
                String mobName = goal.getTargetResource() != null ? goal.getTargetResource().getPath().replace("minecraft:", "") : "enemigo";
                yield "Cazar " + goal.getAmount() + " " + mobName;
            }
            case GO_TO -> {
                BlockPos p = goal.getTargetPos();
                yield "Viajar a " + (p != null ? p.toShortString() : "posición");
            }
            case DEPOSIT -> "Guardar objetos en cofre / baúl";
            case FOLLOW -> "Seguir al propietario";
            case STAY -> "Esperar en el lugar";
            case RETURN_HOME -> "Regresar a la base / casa";
            case RETURN_OWNER -> "Regresar y entregar al propietario";
            case BUILD -> "Construir estructura (" + goal.getStringParam() + ")";
            case FARM -> "Cuidar granja y cosechar cultivos";
            case GUARD -> "Vigilar y patrullar perímetro";
            case MAINTAIN_CHEST -> "Organizar y mantener cofre";
            default -> goal.getType().name();
        };
    }

    private static boolean hasResourceKeyword(String s) {
        for (String ore : new String[]{"diamante", "oro", "hierro", "carbon", "cobre", "redstone", "esmeralda", "lapis", "netherita", "cuarzo", "piedra", "luminosa", "almas", "debris", "blackstone", "basalto", "obsidiana", "netherrack"}) {
            if (s.contains(ore)) return true;
        }
        for (String w : new String[]{"roble", "abedul", "abeto", "jungla", "acacia", "manglar", "cerezo", "bambu", "madera", "arbol"}) {
            if (s.contains(w)) return true;
        }
        for (String m : new String[]{"creeper", "zombie", "esqueleto", "arana", "enderman", "vaca", "cerdo", "oveja", "pollo"}) {
            if (s.contains(m)) return true;
        }
        return false;
    }
}
