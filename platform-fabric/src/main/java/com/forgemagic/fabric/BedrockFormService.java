package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import com.forgemagic.api.SkillAction;
import com.forgemagic.api.SkillDefinition;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Optional Bedrock UI bridge. It uses reflection so the Fabric mod remains valid without
 * Floodgate/Cumulus, while connected Bedrock players get native tappable forms.
 */
public final class BedrockFormService {
    private static final String ICON_ROOT = "textures/ui/forge_solo/icons/";
    private static final String CUMULUS_IMAGE_TYPE = "org.geysermc.cumulus.util.FormImage$Type";

    private BedrockFormService() { }

    public static boolean showSkillPanel(ServerPlayer player, List<SkillDefinition> skills, Consumer<String> onSkill) {
        if (skills == null || skills.isEmpty()) return false;

        String className = classNameFrom(skills.getFirst().id());
        String content = "Tap an ability to cast. Mana and cooldowns are checked by the server.\n"
                + "Crouch and tap your class weapon to reopen, or use /skills panel.\n\n"
                + skills.stream()
                .map(skill -> "§b" + skill.displayName() + "§r — " + skill.description())
                .collect(Collectors.joining("\n\n"));
        List<String> labels = skills.stream().map(BedrockFormService::skillButtonLabel).toList();
        List<String> images = skills.stream().map(skill -> ICON_ROOT + iconFor(skill.action())).toList();
        return showButtons(player, "✦ " + className + " SKILL DECK", content, labels, images, index -> {
            if (index >= 0 && index < skills.size()) onSkill.accept(skills.get(index).id());
        });
    }

    public static boolean showClassPanel(ServerPlayer player, List<ClassId> options, Consumer<ClassId> onClass) {
        List<String> labels = options.stream().map(BedrockFormService::classButtonLabel).toList();
        List<String> images = options.stream().map(clazz -> ICON_ROOT + iconFor(clazz)).toList();
        return showButtons(player, "CHOOSE YOUR CLASS",
                "Choose carefully: your class is permanent after selection.\nTap one of the five class buttons to continue.",
                labels, images, index -> {
                    if (index >= 0 && index < options.size()) onClass.accept(options.get(index));
                });
    }

    /** Kept as a plain-button entry point for future forms that do not have pack artwork. */
    public static boolean showButtons(ServerPlayer player, String title, String content,
                                       List<String> labels, Consumer<Integer> onSelect) {
        return showButtons(player, title, content, labels, Collections.nCopies(labels.size(), ""), onSelect);
    }

    private static boolean showButtons(ServerPlayer player, String title, String content,
                                       List<String> labels, List<String> images, Consumer<Integer> onSelect) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object floodgatePlayer = apiClass.getMethod("getPlayer", java.util.UUID.class)
                    .invoke(api, player.getUUID());
            if (floodgatePlayer == null) return false;

            Class<?> simpleFormClass = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simpleFormClass.getMethod("builder").invoke(null);
            invokeFluent(builder, "title", String.class, title);
            invokeFluent(builder, "content", String.class, content);

            Method buttonMethod = findMethod(builder.getClass(), "button", String.class, Consumer.class);
            if (buttonMethod == null) return false;

            Method imageButtonMethod = null;
            Object pathType = null;
            try {
                Class<?> imageTypeClass = Class.forName(CUMULUS_IMAGE_TYPE);
                imageButtonMethod = findImageButtonMethod(builder.getClass(), imageTypeClass);
                pathType = enumValue(imageTypeClass, "PATH");
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Older or partial Cumulus integrations still get usable text buttons.
            }

            for (int index = 0; index < labels.size(); index++) {
                final int selected = index;
                Consumer<Object> callback = response -> player.level().getServer()
                        .execute(() -> onSelect.accept(selected));
                String imagePath = index < images.size() ? images.get(index) : "";
                if (imageButtonMethod != null && pathType != null && imagePath != null && !imagePath.isBlank()) {
                    try {
                        imageButtonMethod.invoke(builder, labels.get(index), pathType, imagePath, callback);
                    } catch (ReflectiveOperationException | RuntimeException imageFailure) {
                        buttonMethod.invoke(builder, labels.get(index), callback);
                    }
                } else {
                    buttonMethod.invoke(builder, labels.get(index), callback);
                }
            }

            Object form = builder.getClass().getMethod("build").invoke(builder);
            Method sendForm = findSingleArgumentMethod(floodgatePlayer.getClass(), "sendForm");
            if (sendForm == null) return false;
            Object result = sendForm.invoke(floodgatePlayer, form);
            return !(result instanceof Boolean bool) || bool;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static String skillButtonLabel(SkillDefinition skill) {
        return skill.displayName() + "\n" + String.format(Locale.ROOT, "%.0f mana · %.0fs cooldown",
                skill.baseMana(), skill.baseCooldownSeconds());
    }

    private static String classNameFrom(String skillId) {
        int separator = skillId == null ? -1 : skillId.indexOf('.');
        String value = separator < 0 ? "FERN" : skillId.substring(0, separator);
        return value.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static String classButtonLabel(ClassId clazz) {
        return switch (clazz) {
            case LUMBER -> "LUMBER · HEARTWOOD";
            case MINER -> "MINER · ETHERIUM";
            case CRAFTSMAN -> "CRAFTSMAN · MASTERWORK";
            case MARKSMAN -> "MARKSMAN · STORMCALLER";
            case HEALER -> "HEALER · WISE TOME";
            default -> clazz.name();
        };
    }

    private static String iconFor(ClassId clazz) {
        return switch (clazz) {
            case LUMBER -> "nature.png";
            case MINER -> "arcane.png";
            case CRAFTSMAN -> "impact.png";
            case MARKSMAN -> "shadow.png";
            case HEALER -> "radiant.png";
            default -> "arcane.png";
        };
    }

    private static String iconFor(SkillAction action) {
        return switch (action) {
            case HEAL, REGENERATION, PURIFY, ABSORPTION, ABSORB_DAMAGE, DAMAGE_REDUCTION -> "radiant.png";
            case ROOT, SLOW -> "shadow.png";
            case PROJECTILE_DAMAGE, ARROW_ACCURACY, ARROW_RECOVERY, KNOCKBACK -> "impact.png";
            case NIGHT_VISION, GLOW -> "arcane.png";
            case TREE_FELL, ORE_SENSE, ORE_PULL, AREA_MINE, HASTE, SPEED, FURNACE_SPEED,
                    CRAFT_REFUND, TOOL_REPAIR, MATERIAL_BONUS, PASSIVE_DURABILITY -> "nature.png";
            default -> "arcane.png";
        };
    }

    private static void invokeFluent(Object target, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, parameter);
        method.invoke(target, value);
    }

    private static Method findMethod(Class<?> type, String name, Class<?> first, Class<?> second) {
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals(name) && parameters.length == 2
                    && parameters[0] == first && Consumer.class.isAssignableFrom(parameters[1])) {
                return method;
            }
        }
        return null;
    }

    private static Method findImageButtonMethod(Class<?> type, Class<?> imageTypeClass) {
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals("button") && parameters.length == 4
                    && parameters[0] == String.class && parameters[1] == imageTypeClass
                    && parameters[2] == String.class && Consumer.class.isAssignableFrom(parameters[3])) {
                return method;
            }
        }
        return null;
    }

    private static Object enumValue(Class<?> enumClass, String name) {
        if (!enumClass.isEnum()) return null;
        for (Object value : enumClass.getEnumConstants()) {
            if (value instanceof Enum<?> constant && constant.name().equals(name)) return value;
        }
        return null;
    }

    private static Method findSingleArgumentMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) return method;
        }
        return null;
    }
}
