package com.tisam.client.screen;

import com.hellow.decor.Decoration;
import com.hellow.decor.DecoratedElement;
import com.hellow.decor.GradientRing;
import com.hellow.decor.Sparkles;
import com.hellow.element.BoxElement;
import com.hellow.element.ButtonElement;
import com.hellow.element.Element;
import com.hellow.element.Elements;
import com.hellow.element.LinkElement;
import com.hellow.element.ListView;
import com.hellow.element.RenderContext;
import com.hellow.element.RowElement;
import com.hellow.element.ScrollElement;
import com.hellow.element.TextElement;
import com.hellow.layout.Justify;
import com.hellow.layout.Spacing;
import com.hellow.modal.HellowModal;
import com.hellow.state.State;
import com.hellow.style.HellowColor;
import com.hellow.style.HellowTheme;
import com.hellow.text.StringMode;
import com.tisam.TisamConstants;
import com.tisam.client.TisamClientEvents;
import com.tisam.config.ModuleEntry;
import com.tisam.config.ModuleEntry.ContentType;
import com.tisam.config.ModuleEntry.Priority;
import com.tisam.config.ModulesConfig;
import com.tisam.config.PreferencesData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recommended-content list: a tabbed, scrollable card browser.
 *
 * <p>Chrome (dim layer, dialog box, title strip, bottom buttons) comes from
 * {@link HellowModal}. The card list is a hellow {@link ListView} — fixed row height,
 * rows built on demand, viewport-culled, wheel-scrollable — so a long list never
 * materializes hundreds of element objects. Each card is a {@link ModuleCardElement}
 * (a custom hellow {@link Element}) that reproduces the pre-migration card art exactly
 * and gains hover tooltips for free.
 */
public final class ModListScreen extends HellowModal {
    private static final int CARD_HEIGHT = 70;
    private static final int CARD_GAP = 6;
    private static final int TAB_ROW_HEIGHT = 20;
    private static final int GUIDE_BUTTON_HEIGHT = 20;
    private static final int BODY_HINT_MARGIN = 4;
    /** Vertical offset inside the body where the tab row and guide button live. */
    private static final int HEADER_OFFSET = BODY_HINT_MARGIN + TAB_ROW_HEIGHT + BODY_HINT_MARGIN
            + GUIDE_BUTTON_HEIGHT + BODY_HINT_MARGIN;

    private final ModulesConfig modules;
    private final List<ModuleEntry> missing;
    private final PreferencesData prefs;

    /** Window title breathes gold on the hellow-owned animated clock. */
    private static final HellowColor TITLE_BREATH = HellowColor.animated(List.of(0xFFFFD700, 0xFFB8960C), 3f);

    private final Map<ContentType, List<ModuleEntry>> byType = new EnumMap<>(ContentType.class);
    private ContentType activeType = ContentType.MOD;
    private List<ModuleEntry> activeList = List.of();

    private boolean showInstallGuide = false;
    private Element listView;
    /** Auto-wrapped guide panel (scrolls when the text overflows the viewport). */
    private Element guideScroll;
    private ContentType guideBuiltFor;
    private Element tabRow;
    private Element guideButton;
    /**
     * Scroll offset of the mod list, kept across {@code rebuildListView()} calls:
     * HellowModal re-inits (and rebuilds the list) whenever this screen becomes
     * current again — e.g. returning from the external-link confirm — and a fresh
     * ListView would otherwise reset the position to the top.
     */
    private final State<Integer> listScrollState = State.of(0);

    public ModListScreen(TitleScreen parent, ModulesConfig modules,
                         List<ModuleEntry> missing, PreferencesData prefs) {
        // Window title breathes gold via the theme's HellowColor slot (hellow-owned
        // animated clock — guaranteed to advance in every environment).
        super(Component.literal(TisamConstants.MOD_NAME), parent,
                HellowTheme.DEFAULT.withTitle(TITLE_BREATH));
        this.modules = modules;
        this.missing = missing;
        this.prefs = prefs;
        this.dialogWidth = 560;
        this.dialogHeight = 340;
        rebuildBuckets();
        this.activeType = pickDefaultTab();
        this.activeList = this.byType.getOrDefault(this.activeType, List.of());
    }

    /**
     * True when at least one still-missing entry {@code can_ignore=false}: those are
     * "must install" mods, so Later / Never Remind would otherwise skip them forever
     * (the buttons are greyed out in that case).
     */
    private boolean hasNonIgnorable() {
        for (ModuleEntry m : this.missing) {
            if (!m.canIgnore) return true;
        }
        return false;
    }

    private void rebuildBuckets() {
        this.byType.clear();
        EnumMap<ContentType, List<ModuleEntry>> tmp = new EnumMap<>(ContentType.class);
        for (ModuleEntry m : this.missing) {
            ContentType t = m.type == null ? ContentType.MOD : m.type;
            tmp.computeIfAbsent(t, k -> new ArrayList<>()).add(m);
        }
        for (Map.Entry<ContentType, List<ModuleEntry>> e : tmp.entrySet()) {
            e.getValue().sort(ModuleEntry.BY_PRIORITY);
        }
        this.byType.putAll(tmp);
    }

    private ContentType pickDefaultTab() {
        for (ContentType t : ContentType.values()) {
            List<ModuleEntry> list = this.byType.get(t);
            if (list != null && !list.isEmpty()) return t;
        }
        return ContentType.MOD;
    }

    @Override
    protected void init() {
        resetBottomButtons();
        boolean locked = hasNonIgnorable();
        addButton(Elements.button(Component.translatable("tisam.button.later").getString(),
                this::laterReminder, !locked));
        addButton(Elements.button(Component.translatable("tisam.button.never_remind").getString(),
                this::openDisclaimer, !locked));
        addButton(Elements.button(Component.translatable("tisam.modlist.exit_game").getString(),
                () -> Minecraft.getInstance().stop()));
        super.init();

        // Tab row: one hellow button per content type, disabled when that tab is empty.
        // Equal-width slots (EqualWidthRow) so the row always fills exactly the body
        // width — flex's "never shrink" rule let a tight window overflow the rightmost
        // tab past the dialog edge, so equal slots own the whole width instead.
        java.util.List<Element> tabs = new java.util.ArrayList<>();
        for (ContentType t : ContentType.values()) {
            int count = this.byType.getOrDefault(t, List.of()).size();
            String label = Component.translatable("tisam.modlist.tab." + t.name().toLowerCase()).getString()
                    + " (" + count + ")";
            tabs.add(new ButtonElement(label, () -> switchTab(t), null, null, null, count > 0));
        }
        this.tabRow = Elements.row(Justify.EQUAL, 4, tabs.toArray(new Element[0]));

        // Guide toggle button below the tabs.
        this.guideButton = new ButtonElement(
                Component.translatable("tisam.modlist.guide").getString(),
                () -> this.showInstallGuide = !this.showInstallGuide);

        rebuildListView();
    }

    /** Builds a fresh ListView for the active tab (fresh instance, offset state shared). */
    private void rebuildListView() {
        int viewport = listViewportHeight();
        this.listView = new ListView(this.activeList.size(), CARD_HEIGHT,
                CARD_GAP, viewport, index -> new ModuleCardElement(this.activeList.get(index), this::openLink),
                this.listScrollState);
    }

    private int listViewportHeight() {
        return Math.max(0, bodyHeight() - HEADER_OFFSET - BODY_HINT_MARGIN);
    }

    private int listTop() {
        return bodyTop() + HEADER_OFFSET;
    }

    @Override
    public void tick() {
        super.tick();
        // Drive the inertia fling of the list and the guide panel.
        if (!this.showInstallGuide && this.listView instanceof ListView lv) {
            lv.advanceFling();
        }
        if (this.showInstallGuide && this.guideScroll instanceof ScrollElement se) {
            se.advanceFling(listContext(0, 0));
        }
    }

    @Override
    protected void renderBody(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.tabRow != null) {
            RenderContext tabCtx = new RenderContext(g, this.font, bodyLeft(),
                    bodyTop() + BODY_HINT_MARGIN, bodyWidth(), mouseX, mouseY);
            this.tabRow.render(tabCtx);
            RenderContext guideCtx = new RenderContext(g, this.font, bodyLeft(),
                    bodyTop() + BODY_HINT_MARGIN + TAB_ROW_HEIGHT + BODY_HINT_MARGIN,
                    bodyWidth(), mouseX, mouseY);
            this.guideButton.render(guideCtx);
        }
        if (this.showInstallGuide) {
            renderGuide(g, mouseX, mouseY);
            return;
        }
        if (this.activeList.isEmpty()) {
            g.drawString(this.font, Component.translatable("tisam.modlist.empty_for_type"),
                    bodyLeft(), listTop(), TisamConstants.TEXT_MUTED, true);
            return;
        }
        RenderContext ctx = new RenderContext(g, this.font, bodyLeft(), listTop(),
                bodyWidth(), mouseX, mouseY);
        this.listView.render(ctx);
        Component tooltip = this.listView.tooltipAt(ctx);
        if (tooltip != null) {
            g.renderTooltip(this.font, List.of(tooltip),
                    Optional.<net.minecraft.world.inventory.tooltip.TooltipComponent>empty(), mouseX, mouseY);
        }
    }

    /**
     * Guide panel: each newline-then-width-wrapped paragraph is a {@link TextElement}
     * inside a {@link ScrollElement}, so long guides wrap automatically and — when the
     * wrapped text exceeds the viewport — a scrollbar appears automatically. Rebuilt
     * only when the active tab changes.
     */
    private void renderGuide(GuiGraphics g, int mouseX, int mouseY) {
        if (this.guideBuiltFor != this.activeType || this.guideScroll == null) {
            this.guideScroll = buildGuideElement();
            this.guideBuiltFor = this.activeType;
        }
        RenderContext ctx = new RenderContext(g, this.font, bodyLeft(), listTop(),
                bodyWidth(), mouseX, mouseY);
        this.guideScroll.render(ctx);
        Component tooltip = this.guideScroll.tooltipAt(ctx);
        if (tooltip != null) {
            g.renderTooltip(this.font, List.of(tooltip),
                    Optional.<net.minecraft.world.inventory.tooltip.TooltipComponent>empty(), mouseX, mouseY);
        }
    }

    private Element buildGuideElement() {
        String guide = unescapeNewlines(guideFor(this.activeType));
        List<Element> paragraphs = new ArrayList<>();
        for (String raw : guide.split("\n")) {
            // WRAP mode soft-wraps inside the available width; height grows with lines.
            paragraphs.add(new TextElement(Component.literal(raw), TisamConstants.GUIDE_BODY_COLOR, true));
        }
        return new ScrollElement(paragraphs, 4, listViewportHeight());
    }

    /** Turns literal {@code \n} (backslash + n from a mis-escaped JSON string) into real newlines. */
    private static String unescapeNewlines(String s) {
        return s == null ? "" : s.replace("\\n", "\n");
    }

    /**
     * Per-entry override > global config > per-type default. The guide panel shows one
     * guide for the active tab, so the "per-entry override" is the first entry of the
     * active list that carries its own {@code install_guide_text}.
     */
    private String guideFor(ContentType type) {
        for (ModuleEntry entry : this.activeList) {
            if (entry.installGuideText != null && !entry.installGuideText.isEmpty()) {
                return entry.installGuideText;
            }
        }
        if (this.modules.installGuideText != null
                && !this.modules.installGuideText.equals(TisamConstants.DEFAULT_INSTALL_GUIDE_TEXT)) {
            return this.modules.installGuideText;
        }
        return TisamConstants.DEFAULT_INSTALL_GUIDE_BY_TYPE
                .getOrDefault(type, TisamConstants.DEFAULT_INSTALL_GUIDE_TEXT);
    }

    // -------- hellow element event forwarding (PageScreen-style, null-g contexts) --------

    private RenderContext listContext(int mouseX, int mouseY) {
        return new RenderContext(null, this.font, bodyLeft(), listTop(), bodyWidth(), mouseX, mouseY);
    }

    private RenderContext tabContext(int mouseX, int mouseY) {
        return new RenderContext(null, this.font, bodyLeft(), bodyTop() + BODY_HINT_MARGIN, bodyWidth(), mouseX, mouseY);
    }

    private RenderContext guideButtonContext(int mouseX, int mouseY) {
        return new RenderContext(null, this.font, bodyLeft(),
                bodyTop() + BODY_HINT_MARGIN + TAB_ROW_HEIGHT + BODY_HINT_MARGIN, bodyWidth(), mouseX, mouseY);
    }

    /** Forwards a header (tab row + guide toggle) hit; returns true if consumed. */
    private boolean headerClicked(double mouseX, double mouseY, int button) {
        if (this.tabRow != null
                && this.tabRow.mouseClicked(tabContext((int) mouseX, (int) mouseY), button)) {
            return true;
        }
        return this.guideButton != null
                && this.guideButton.mouseClicked(guideButtonContext((int) mouseX, (int) mouseY), button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (headerClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!this.showInstallGuide && this.listView != null
                && this.listView.mouseClicked(listContext((int) mouseX, (int) mouseY), button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.tabRow != null
                && this.tabRow.mouseScrolled(tabContext((int) mouseX, (int) mouseY), deltaX, deltaY)) {
            return true;
        }
        if (this.guideButton != null
                && this.guideButton.mouseScrolled(guideButtonContext((int) mouseX, (int) mouseY), deltaX, deltaY)) {
            return true;
        }
        if (this.showInstallGuide) {
            if (this.guideScroll != null
                    && this.guideScroll.mouseScrolled(listContext((int) mouseX, (int) mouseY), deltaX, deltaY)) {
                return true;
            }
        } else if (this.listView != null
                && this.listView.mouseScrolled(listContext((int) mouseX, (int) mouseY), deltaX, deltaY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.tabRow != null
                && this.tabRow.mouseDragged(tabContext((int) mouseX, (int) mouseY), button, dragX, dragY)) {
            return true;
        }
        if (this.guideButton != null
                && this.guideButton.mouseDragged(guideButtonContext((int) mouseX, (int) mouseY), button, dragX, dragY)) {
            return true;
        }
        if (this.showInstallGuide) {
            if (this.guideScroll != null
                    && this.guideScroll.mouseDragged(listContext((int) mouseX, (int) mouseY), button, dragX, dragY)) {
                return true;
            }
        } else if (this.listView != null
                && this.listView.mouseDragged(listContext((int) mouseX, (int) mouseY), button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // -------- Actions --------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC = "Later". When a must-install (can_ignore=false) module is missing,
        // Later/Never are locked — ESC just leaves the screen (no reminder consumed,
        // the next title-screen visit will prompt again).
        if (keyCode == 256) {
            if (hasNonIgnorable()) {
                Minecraft.getInstance().setScreen(this.parent);
            } else {
                laterReminder();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        Sparkles.clearAll();
    }

    private void switchTab(ContentType type) {
        this.activeType = type;
        this.activeList = this.byType.getOrDefault(type, List.of());
        // A new tab starts at the top — the shared scroll state must be reset, or
        // switching tabs would inherit the previous tab's position.
        this.listScrollState.set(0);
        rebuildListView();
        // The guide text depends on the active tab; force a rebuild next render.
        this.guideBuiltFor = null;
    }

    private void openLink(String url) {
        // Parent = this screen, so Cancel/Open return to the mod list with the current
        // tab and scroll position preserved (not the title screen).
        Minecraft.getInstance().setScreen(new ExternalLinkConfirmScreen(this, url));
    }

    private void laterReminder() {
        TisamClientEvents.onPromptClosed(true);
        Minecraft.getInstance().setScreen(this.parent);
    }

    private void openDisclaimer() {
        Minecraft.getInstance().setScreen(new DisclaimerScreen(this, this.parent, modules, prefs));
    }

    /**
     * One card in the list — a hellow element tree wrapped in the decorations that
     * used to be hand-drawn here: the gradient ring, the entry background, and the
     * drifting sparkles on the animated tiers.
     */
    private static final class ModuleCardElement implements Element {
        private static final int CONTENT_LEFT = 4;
        private static final int CONTENT_TOP = 5;

        /**
         * Gradient border pairs (start → end), LegendaryTooltips-style: the start
         * color breathes on the hellow clock, the end color is a fixed darker tone.
         * Drawn as a full-card gradient with the content background inset by 1px,
         * leaving a 1px gradient ring.
         */
        private record CardBorder(HellowColor start, HellowColor end) {}

        private static final CardBorder NORMAL_BORDER = new CardBorder(
                HellowColor.of(0xFF888888), HellowColor.of(0xFF5A5A5A));
        private static final CardBorder CRITICAL_BORDER = new CardBorder(
                HellowColor.animated(List.of(0xFFFF6655, 0xFFD04035), 1.5f), HellowColor.of(0xFF992015));
        private static final CardBorder FEATURED_BORDER = new CardBorder(
                HellowColor.animated(List.of(0xFFFFD700, 0xFFE0BE30), 1.8f), HellowColor.of(0xFFB8960C));
        private static final CardBorder LOCKED_BORDER = new CardBorder(
                HellowColor.animated(List.of(0xFFB08CFF, 0xFF9A76E0), 2.0f), HellowColor.of(0xFF5A3F96));

        private final ModuleEntry entry;
        private final java.util.function.Consumer<String> onOpenLink;
        private final Element content;
        private final Element shell;

        ModuleCardElement(ModuleEntry entry, java.util.function.Consumer<String> onOpenLink) {
            this.entry = entry;
            this.onOpenLink = onOpenLink;
            // Build the content tree eagerly: ListView re-creates each row element on
            // every frame (visibility culling), so a lazily-built content field would
            // only ever exist on the render instance — the mouse-click instance would
            // still have null content and swallow the click ("hover works, click dead").
            this.content = buildContent();
            this.shell = buildShell();
        }

        /**
         * The card shell as generic decorations: the gradient ring on every card,
         * plus the spark swarm on the animated tiers. The swarm is keyed by entry
         * id so the list rebuild does not restart it. Padding comes from a
         * borderless box, so the decoration rectangle covers the whole card.
         */
        private Element buildShell() {
            CardBorder border = borderFor();
            List<Decoration> decorations = new java.util.ArrayList<>();
            decorations.add(new GradientRing(1, border.start(), border.end(), HellowColor.of(bgColor())));
            if (border != NORMAL_BORDER) {
                // Peak opacity matches the old hand-drawn swarm (0xB4) while the
                // animated border color keeps breathing.
                HellowColor tint = () -> (border.start().argb() & 0xFFFFFF) | 0xB4000000;
                decorations.add(new Sparkles(10, 1, 1f, tint, "tisam:card:" + this.entry.id));
            }
            Element padded = new BoxElement(this.content, Spacing.NONE,
                    new Spacing(CONTENT_LEFT, CONTENT_TOP, CONTENT_LEFT, 0), 0, 0);
            return new DecoratedElement(padded, decorations);
        }

        @Override
        public int width(Font font) {
            // Bounded by the container; the ListView hands each row its viewport width.
            return 0;
        }

        @Override
        public int height(Font font, int width) {
            return CARD_HEIGHT;
        }

        private int bgColor() {
            if (!this.entry.canIgnore) return TisamConstants.CARD_BG_LOCKED;
            return this.entry.priority == Priority.FEATURED ? TisamConstants.CARD_BG_FEATURED
                    : this.entry.priority == Priority.CRITICAL ? TisamConstants.CARD_BG_CRITICAL
                    : TisamConstants.CARD_BG_NORMAL;
        }

        private CardBorder borderFor() {
            if (!this.entry.canIgnore) return LOCKED_BORDER;
            if (this.entry.priority == Priority.FEATURED) return FEATURED_BORDER;
            if (this.entry.priority == Priority.CRITICAL) return CRITICAL_BORDER;
            return NORMAL_BORDER;
        }

        @Override
        public void render(RenderContext ctx) {
            this.shell.render(ctx);
        }

        /** Declare the card content as a hellow element tree. */
        private Element buildContent() {
            boolean locked = !this.entry.canIgnore;
            java.util.List<Element> top = new java.util.ArrayList<>();
            top.add(new TextElement(Component.literal(this.entry.name), TisamConstants.CARD_NAME_COLOR));
            if (locked) {
                top.add(new TextElement(Component.literal("\uD83D\uDD12"), TisamConstants.CARD_BORDER_LOCKED));
            }
            top.add(Elements.fillSpacer());
            String typeLabel = this.entry.type != null ? this.entry.type.name() : "MOD";
            top.add(new TextElement(Component.literal(typeLabel), TisamConstants.CARD_TYPE_COLOR));

            java.util.List<Element> bottom = new java.util.ArrayList<>();
            if (this.entry.license != null && !this.entry.license.isEmpty()) {
                bottom.add(new TextElement(Component.literal(this.entry.license), TisamConstants.CARD_TYPE_COLOR));
            }
            bottom.add(Elements.fillSpacer());
            String priorityLabel = locked ? "REQUIRED"
                    : (this.entry.priority != null ? this.entry.priority.name() : "NORMAL");
            int priorityColor = locked ? TisamConstants.CARD_BORDER_LOCKED
                    : this.entry.priority == Priority.CRITICAL ? TisamConstants.CARD_BORDER_CRITICAL
                    : this.entry.priority == Priority.FEATURED ? TisamConstants.CARD_BORDER_FEATURED
                    : TisamConstants.CARD_TYPE_COLOR;
            bottom.add(new TextElement(Component.literal(priorityLabel), priorityColor));

            java.util.List<Element> rows = new java.util.ArrayList<>();
            rows.add(new RowElement(top, 6));
            // fill(...) stretches the CLAMPED cell across the column's spare width —
            // ColumnElement hands fill children the shared spare, so the ellipsis
            // truncation actually gets a bounded width (without fill the cell is
            // measured at its full natural width and never truncates).
            rows.add(Elements.fill(TextElement.clamped(Component.literal(this.entry.description),
                    TisamConstants.CARD_DESC_COLOR)));
            rows.add(new RowElement(bottom, 6));
            if (this.entry.url != null && !this.entry.url.isEmpty()) {
                rows.add(Elements.fill(new LinkElement(Component.literal(this.entry.url), this.entry.url,
                        HellowColor.of(TisamConstants.URL_ICON_COLOR), this.onOpenLink,
                        Component.literal(this.entry.url), StringMode.CLAMPED)));
            }
            return new com.hellow.element.ColumnElement(rows, 2);
        }

        @Override
        public boolean mouseClicked(RenderContext ctx, int button) {
            return this.shell.mouseClicked(ctx, button);
        }

        @Override
        public Component tooltipAt(RenderContext ctx) {
            return this.shell.tooltipAt(ctx);
        }
    }
}