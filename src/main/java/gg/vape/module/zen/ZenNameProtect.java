package gg.vape.module.zen;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventChat;
import gg.vape.event.impl.EventNameFormat;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.value.ModeValue;
import gg.vape.value.BooleanValue;
import gg.vape.value.StringValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ITextComponent;
import gg.vape.wrapper.impl.World;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zen NameProtect (名称保护) — replaces the local player name in name tags and optionally chat.
 * Ported from OpenZen's misc/NameProtect.java: Fixed (own string) and Random (random other player).
 */
public class ZenNameProtect
extends Mod {
    private static final int MOD_ID = 1607423558;
    private static final long RANDOM_REFRESH_MILLIS = 30000L;

    private final ModeOption fixedMode = new ModeOption("Fixed");
    private final ModeOption randomMode = new ModeOption("Random");
    private final ModeValue mode = ModeValue.create(this, "Mode",
            "Fixed - use the name below\nRandom - borrow a random player name", this.fixedMode, this.fixedMode, this.randomMode);
    private final StringValue name = StringValue.create(this, "Name", "Player");
    private final BooleanValue chat = BooleanValue.create(this, "Chat", false, "Also replace the name in chat messages.");

    private String replacement = "Player";
    private String realName = "";
    private long lastRandom = 0L;

    public ZenNameProtect() {
        super("Zen NameProtect", MOD_ID, Category.ZEN,
                "Hides your real name in name tags and chat, ported from OpenZen's NameProtect.");
        this.mode.addModeDependentValues(this.fixedMode, this.name);
    }

    @Override
    public void onEnable() {
        this.refresh(true);
    }

    private void refresh(boolean force) {
        EntityPlayerSP self = ZenUtil.player();
        this.realName = self == null ? "" : this.plainName(self);
        if (this.mode.getSelectedIndex() == 0) {
            String fixed = this.name.getValue();
            this.replacement = fixed == null || fixed.trim().isEmpty() ? "Player" : fixed.trim();
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - this.lastRandom < RANDOM_REFRESH_MILLIS && !this.replacement.isEmpty()) {
            return;
        }
        this.lastRandom = now;
        this.replacement = this.pickRandomName();
    }

    private String pickRandomName() {
        World world = ZenUtil.world();
        List<String> candidates = new ArrayList<String>();
        if (world != null) {
            Object selfHandle = null;
            try {
                selfHandle = ZenUtil.player() == null ? null : ZenUtil.player().getObject();
            }
            catch (Throwable ignored) {
                selfHandle = null;
            }
            for (Object handle : world.z()) {
                Entity entity = new Entity(handle);
                if (entity.isNull() || !entity.isInstance(gg.vape.mapping.MappedClasses.Yl)) {
                    continue;
                }
                if (selfHandle != null && selfHandle == entity.getObject()) {
                    continue;
                }
                String candidate = this.plainName(new EntityPlayer(handle));
                if (candidate != null && !candidate.isEmpty()) {
                    candidates.add(candidate);
                }
            }
        }
        if (candidates.isEmpty()) {
            return "Player" + ThreadLocalRandom.current().nextInt(100, 1000);
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private String plainName(EntityPlayer player) {
        try {
            String formatted = player.getName();
            if (formatted == null) {
                return "";
            }
            return formatted.replaceAll("\u00a7.", "").trim();
        }
        catch (Throwable ignored) {
            return "";
        }
    }

    @EventHandler
    public void onNameFormat(EventNameFormat event) {
        EntityPlayer target = event.getPlayer();
        EntityPlayerSP self = ZenUtil.player();
        if (target == null || self == null) {
            return;
        }
        Object targetHandle;
        Object selfHandle;
        try {
            targetHandle = target.getObject();
            selfHandle = self.getObject();
        }
        catch (Throwable ignored) {
            return;
        }
        if (targetHandle == null || targetHandle != selfHandle) {
            return;
        }
        this.refresh(false);
        if (this.replacement.isEmpty()) {
            return;
        }
        if (event.getDisplayName() != null && this.replacement.equals(event.getDisplayName().getFormattedText())) {
            return;
        }
        event.setDisplayName(ITextComponent.P(this.replacement));
    }

    @EventHandler
    public void onChat(EventChat event) {
        if (!this.chat.getEffectiveValue().booleanValue()) {
            return;
        }
        ITextComponent message = event.getMessage();
        if (message == null || this.realName.isEmpty()) {
            return;
        }
        this.refresh(false);
        String text = message.getFormattedText();
        if (text == null || !text.contains(this.realName)) {
            return;
        }
        event.setMessage(ITextComponent.P(text.replace(this.realName, this.replacement)));
    }
}
