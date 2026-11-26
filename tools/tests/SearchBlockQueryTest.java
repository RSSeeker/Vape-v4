import gg.vape.ui.unmap.SearchBlock;
import gg.vape.utils.BlockUtil;

public final class SearchBlockQueryTest {
    private static int checks;
    private static void check(boolean value, String message) {
        ++checks;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        SearchBlock barrel = new SearchBlock("minecraft:barrel", 0);
        check(barrel.M() == 42 && barrel.b(42, -1), "vanilla resource key resolves as a block, not metadata");
        SearchBlock uppercase = new SearchBlock(" MINECRAFT:BARREL ", 0);
        check(uppercase.M() == 42, "vanilla namespace is trimmed and normalized");
        SearchBlock meta = new SearchBlock("chest:2", 0);
        check(meta.M() == 54 && meta.i() == 2 && meta.b(54, 2) && !meta.b(54, 1), "legacy name and metadata still work");
        SearchBlock numeric = new SearchBlock("54:5", 0);
        check(numeric.M() == 54 && numeric.i() == 5 && numeric.b(54, 5), "numeric id and metadata still work");
        SearchBlock namespacedMeta = new SearchBlock("minecraft:chest:3", 0);
        check(namespacedMeta.M() == 54 && namespacedMeta.i() == 3, "vanilla resource key can carry legacy metadata");
        SearchBlock modded = new SearchBlock("mod:thing", 0);
        check(modded.M() == 70 && "mod:thing".equals(BlockUtil.last), "nonnumeric suffix is forwarded intact to the resolver");
        SearchBlock rule = new SearchBlock("rule", 0);
        check(rule.E() != null, "custom character rules keep priority over name parsing");
        SearchBlock missing = new SearchBlock("minecraft:not_a_block", 0);
        check(missing.M() == -1 && !missing.b(42, -1), "unresolved names do not match blocks");
        barrel.M(false);
        check(!barrel.b(42, -1), "disabled target is not matched");
        System.out.println("Search block queries: " + checks + " headless assertions passed");
    }
}
