package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import com.forgemagic.api.SkillDefinition;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/**
 * Optional Bedrock UI bridge. It uses reflection so the Fabric mod remains valid without
 * Floodgate/Cumulus, while connected Bedrock players get native tappable forms.
 */
public final class BedrockFormService {
    private BedrockFormService() { }

    public static boolean showSkillPanel(ServerPlayer player, List<SkillDefinition> skills, Consumer<String> onSkill) {
        String content = "Class skills • mana and cooldowns remain server-authoritative";
        return showButtons(player, "Fern Skill Deck", content,
                skills.stream().map(SkillDefinition::displayName).toList(), index -> {
                    if (index >= 0 && index < skills.size()) onSkill.accept(skills.get(index).id());
                });
    }

    public static boolean showClassPanel(ServerPlayer player, List<ClassId> options, Consumer<ClassId> onClass) {
        return showButtons(player, "Fern Class Selection", "Choose your permanent class.",
                options.stream().map(Enum::name).toList(), index -> {
                    if (index >= 0 && index < options.size()) onClass.accept(options.get(index));
                });
    }

    public static boolean showButtons(ServerPlayer player, String title, String content,
                                       List<String> labels, Consumer<Integer> onSelect) {
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
            for (int index = 0; index < labels.size(); index++) {
                final int selected = index;
                Consumer<Object> callback = response -> player.level().getServer().execute(() -> onSelect.accept(selected));
                buttonMethod.invoke(builder, labels.get(index), callback);
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

    private static Method findSingleArgumentMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) return method;
        }
        return null;
    }
}
