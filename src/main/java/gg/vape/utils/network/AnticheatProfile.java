package gg.vape.utils.network;

/**
 * 服务端反作弊引擎枚举。
 *
 * <p>Disabler 里的每条规则都挂在一个具体引擎上：用户选择目标服务器的反作弊后，
 * 出站包管线只执行匹配的规则，避免把针对 Grim 的补丁打到 ACA 服务器上导致踢人。</p>
 */
public enum AnticheatProfile {
    GRIM("Grim", "Grim"),
    ACA("ACA", "ACA"),
    THEMIS("Themis", "Themis"),
    ALL("All", "ALL");

    private final String name;
    private final String label;

    AnticheatProfile(String name, String label) {
        this.name = name;
        this.label = label;
    }

    public String getName() {
        return this.name;
    }

    public String getLabel() {
        return this.label;
    }

    /**
     * 当前选择是否覆盖给定规则所属的引擎。
     */
    public boolean covers(AnticheatProfile ruleProfile) {
        return this == ALL || ruleProfile == null || this == ruleProfile;
    }
}
