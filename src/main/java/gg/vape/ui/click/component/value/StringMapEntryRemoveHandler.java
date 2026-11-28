package gg.vape.ui.click.component.value;

import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.ui.click.component.value.StringMapEntryComponent;
import gg.vape.ui.click.component.value.StringMapValueComponent;

class StringMapEntryRemoveHandler
implements GuiClickListener {
    final StringMapEntryComponent entry;
    final StringMapValueComponent owner;

    @Override
    public void onPrimaryClick() {
        gg.vape.value.StringMapValue value = StringMapValueComponent.getStringMapValueCompat(this.owner);
        boolean changed = value.getValue().containsKey(this.entry.getKeyText());
        value.removeEntry(this.entry.getKeyText());
        if (changed) value.notifyChanged();
        StringMapValueComponent.refreshEntriesCompat(this.owner);
    }

    StringMapEntryRemoveHandler(StringMapValueComponent stringMapValueComponent, StringMapEntryComponent stringMapEntryComponent) {
        this.owner = stringMapValueComponent;
        this.entry = stringMapEntryComponent;
    }
}
