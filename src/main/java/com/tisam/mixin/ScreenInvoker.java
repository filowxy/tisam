package com.tisam.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link Screen#addRenderableWidget} so TISAM can attach vanilla widgets
 * (the title-screen "View Mods" button) to screens it does not own.
 *
 * <p>A Mixin invoker is used instead of an access transformer: ModDevGradle does
 * not apply ATs to the dev compile classpath, so a plain widened call fails to
 * compile there even though the AT would work at runtime. The invoker works
 * identically in dev and production. The generic signature mirrors the target's
 * — erased it must match {@code (GuiEventListener)GuiEventListener}, so the
 * parameter cannot be narrowed to a concrete type like {@code Button}.
 */
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T tisam$invokeAddRenderableWidget(T widget);
}
