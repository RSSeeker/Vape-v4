package gg.vape.value;

import com.google.common.collect.ImmutableList;
import gg.vape.value.ListValueMutableBackingList;
import gg.vape.value.ListValueSuggestionProvider;
import gg.vape.value.Value;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public abstract class ListValue<C, T extends ListValue<C, T>>
extends Value<List<C>, T> {
    @Nullable
    private ListValueSuggestionProvider suggestionProvider;
    private final String displayName;


    public ListValue(Object owner, String id, String displayName) {
        this(owner, id, displayName, new ArrayList<C>());
    }

    public T setSuggestionProvider(ListValueSuggestionProvider suggestionProvider) {
        this.suggestionProvider = suggestionProvider;
        return (T)this;
    }

    public abstract C createEntry(String value, int legacyIndex);

    public List<C> snapshotEntries() {
        return new ArrayList<C>(this.getValue());
    }

    @Override
    public String getDisplayValue() {
        List<C> list = this.getValue();
        if (list.isEmpty()) {
            return "None";
        }
        if (list.size() == 1) {
            return list.get(0).toString();
        }
        return list.get(0).toString() + " +" + (list.size() - 1);
    }

    @Override
    public List<C> copyValue() {
        return this.snapshotEntries();
    }

    @Nullable
    public ListValueSuggestionProvider getSuggestionProvider() {
        return this.suggestionProvider;
    }

    public ListValue(Object owner, String id, String displayName, List<C> entries) {
        super(owner, id, ImmutableList.copyOf(entries));
        // Constructing a detached definition for a profile preview must not
        // schedule a save of the running client's configuration.
        this.setPersistenceSuppressed(true);
        try {
            this.setDirectValue(new ListValueMutableBackingList<C>(this, entries));
        } finally {
            this.setPersistenceSuppressed(false);
        }
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
