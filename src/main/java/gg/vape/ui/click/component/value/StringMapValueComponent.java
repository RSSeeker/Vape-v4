package gg.vape.ui.click.component.value;

import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.component.value.StringMapEntryComponent;
import gg.vape.ui.click.component.value.StringMapEntryListFrame;
import gg.vape.ui.click.component.value.StringMapEntryRemoveHandler;
import gg.vape.ui.click.component.value.StringMapKeyInputComponent;
import gg.vape.ui.click.component.value.StringMapValueInputComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.value.StringMapValue;
import java.awt.Color;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StringMapValueComponent
extends GuiComponent {
    private boolean legacyFlag;
    private boolean refreshPending;
    private List<Map.Entry<String, String>> renderedEntries;
    private final Frame entriesFrame;
    private final TextInputComponentBase keyInput;
    private final StringMapValue stringMapValue;
    private final SimpleTextLabelComponent titleLabel;
    private final TextButton addButton;
    private final TextInputComponentBase valueInput;

    @Override
    public void F() {
    }

    static TextInputComponentBase getValueInputCompat(StringMapValueComponent component) {
        return component.valueInput;
    }

    @Override
    public void J() {
        super.J();
    }

    static void refreshEntriesCompat(StringMapValueComponent component) {
        component.refreshEntries();
    }

    @Override
    public double x() {
        return 50.0;
    }

    private List<Map.Entry<String, String>> snapshotEntries() {
        List<Map.Entry<String, String>> snapshot = new ArrayList<Map.Entry<String, String>>();
        for (Map.Entry<String, String> entry : this.stringMapValue.getValue().entrySet()) {
            snapshot.add(new AbstractMap.SimpleImmutableEntry<String, String>(entry));
        }
        return snapshot;
    }

    private void refreshEntries() {
        this.renderedEntries = this.snapshotEntries();
        this.entriesFrame.t$src$V$zbu1jn();
        for (Map.Entry<String, String> entry : this.renderedEntries) {
            StringMapEntryComponent row = new StringMapEntryComponent(entry.getKey(), entry.getValue());
            row.setRemoveClickListener(new StringMapEntryRemoveHandler(this, row));
            this.entriesFrame.addChildren(row);
        }
        this.refreshPending = false;
        if (this.getParentFrameComponent() != null) this.getParentFrameComponent().l$src$V$1mibm4x();
    }

    @Override
    public double C() {
        return 62 + Math.min(this.entriesFrame.f().size(), 4) * 19;
    }

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
    }

    @Override
    public void I() {
    }

    static StringMapValue getStringMapValueCompat(StringMapValueComponent component) {
        return component.stringMapValue;
    }

    @Override
    public void H() {
        // Profiles and other editors mutate the same map in place. Compare a
        // snapshot, not the map identity, so this view follows those changes.
        if (this.refreshPending || !this.snapshotEntries().equals(this.renderedEntries)) this.refreshEntries();
        GuiRenderPrimitives.d(this.G$src$D$1b2f02a() + 2.0, this.n() + 2.0, this.A() - 4.0, this.L() - 4.0, StringMapValueComponent.J.r);
        GuiRenderPrimitives.d(this.G$src$D$1b2f02a() + this.A() / 2.0, this.n() + 28.0, 5.0, 1.0f, StringMapValueComponent.J.l);
        this.titleLabel.K(this.G$src$D$1b2f02a() + 2.0);
        this.titleLabel.S(this.n() + 2.0);
        this.keyInput.setShowDisabledOverlay(false);
        this.keyInput.setActionButtonVisible(false);
        this.keyInput.K(this.G$src$D$1b2f02a());
        this.keyInput.S(this.n() + 13.0);
        this.valueInput.setShowDisabledOverlay(false);
        this.valueInput.setActionButtonVisible(false);
        this.valueInput.K(this.G$src$D$1b2f02a());
        this.valueInput.S(this.n() + 28.0);
        this.addButton.setDisabledOverlayColor(StringMapValueComponent.J.r);
        this.addButton.o(28.0);
        this.addButton.Y(12.0);
        this.addButton.K(this.G$src$D$1b2f02a() + this.A() - 33.0);
        this.addButton.S(this.n() + 46.0);
        this.entriesFrame.K(this.G$src$D$1b2f02a() + 3.0);
        this.entriesFrame.S(this.n() + 60.0);
        this.entriesFrame.o(this.A() - 5.0);
        this.entriesFrame.Y(68.0);
        this.entriesFrame.t(68.0);
        this.entriesFrame.setShowDisabledOverlay(false);
        this.entriesFrame.setDisabledOverlayColor(new Color(255, 255, 255, 0));
        this.entriesFrame.setUseExplicitWidth(true);
        this.entriesFrame.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.entriesFrame.N(true);
        this.entriesFrame.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.entriesFrame.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().I(false);
        this.entriesFrame.l$src$V$1mibm4x();
    }

    public StringMapValueComponent(StringMapValue stringMapValue) {
        this.stringMapValue = stringMapValue;
        this.bindValue(stringMapValue);
        this.titleLabel = new SimpleTextLabelComponent(stringMapValue.getName());
        this.keyInput = new StringMapKeyInputComponent(this, stringMapValue.getKeyPlaceholder());
        this.valueInput = new StringMapValueInputComponent(this, stringMapValue.getValuePlaceholder());
        this.addButton = new TextButton("ADD", StringMapValueComponent.J.l);
        this.addButton.setClickListener(() -> {
            String key = this.keyInput.getText(), value = this.valueInput.getText();
            boolean changed = !stringMapValue.getValue().containsKey(key)
                    || !Objects.equals(stringMapValue.getValue().get(key), value);
            stringMapValue.putEntry(key, value);
            if (changed) stringMapValue.notifyChanged();
            this.refreshEntries();
            this.keyInput.setText("");
            this.valueInput.setText("");
        });
        this.entriesFrame = new StringMapEntryListFrame(this);
        this.addChildren(this.titleLabel, this.keyInput, this.valueInput, this.addButton, this.entriesFrame);
        this.refreshPending = true;
    }

    @Override
    public void u() {
    }

}
