package com.forgemagic.fabric;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.IdentifierArgument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TomatEconomyCommandArgumentTest {
    @Test
    void marketItemArgumentAcceptsNamespacedRegistryId() throws Exception {
        StringReader reader = new StringReader("minecraft:cobblestone");

        IdentifierArgument argument = TomatEconomyEntrypoint.itemIdArgument();
        String parsed = argument.parse(reader).toString();

        assertEquals("minecraft:cobblestone", parsed);
        assertEquals(reader.getTotalLength(), reader.getCursor());
    }
}
