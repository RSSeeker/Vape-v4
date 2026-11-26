import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.commons.ClassRemapper;
import jdk.internal.org.objectweb.asm.commons.Remapper;

/**
 * Offline comparison of a build.gradle-obfuscated release with two source builds.
 * It reads class files as data and never loads or runs the inspected classes.
 * This build-time tool uses the ASM bundled in JDK 17; the CI command explicitly
 * exports the two internal ASM packages. It is not part of the product payload.
 */
public final class ObfuscatedClassAudit {
    private static final Pattern OLD_NAME = Pattern.compile("gg/vape/[A-Za-z0-9_$/]+");
    private static final Set<String> WHITELIST = new HashSet<>(Arrays.asList(
            "gg/vape/runtime/NativeBridge", "gg/vape/runtime/ForgePayloadClassLoader",
            "gg/vape/event/impl/EventRenderWorldPassExecutorDrain"));
    // Localization edits and separately reviewed module behavior changes.
    private static final Set<String> EXPECTED_ENGLISH_MODULE_CHANGES = new HashSet<>(Arrays.asList(
            "gg/vape/module/none/ClientSettings", "gg/vape/module/render/SpawnerFinder",
            "gg/vape/module/combat/SilentAura", "gg/vape/module/combat/AimAssist",
            "gg/vape/module/combat/AutoMace", "gg/vape/module/combat/HitSwap",
            "gg/vape/module/combat/Triggerbot", "gg/vape/module/render/Search",
            "gg/vape/module/render/search/SearchBlockChunkScanner",
            "gg/vape/module/combat/automace/AutoMaceRotationController",
            "gg/vape/module/combat/aimassist/AimAssistKissSubModule",
            "gg/vape/module/combat/aimassist/AimAssistRotationSubModule",
            "gg/vape/module/combat/aimassist/AimAssistTargetingSubModule"));

    private static final class Metadata extends ClassVisitor {
        String source;
        int access;
        final List<String> fields = new ArrayList<>();
        final List<String> methods = new ArrayList<>();

        Metadata(byte[] bytes) {
            super(Opcodes.ASM7);
            new ClassReader(bytes).accept(this, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            Collections.sort(fields);
            Collections.sort(methods);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.access = access;
        }

        @Override
        public void visitSource(String source, String debug) {
            this.source = source;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            fields.add(access + ":" + name);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            methods.add(access + ":" + name);
            return null;
        }

        boolean shapeEquals(Metadata other) {
            return access == other.access && Objects.equals(source, other.source)
                    && fields.equals(other.fields) && methods.equals(other.methods);
        }
    }

    static final class Result {
        String randomPrefix;
        int productCount, randomCount, mappedProduct, poolMatches, ambiguous, unmatched;
        int moduleCount, mappedModules, baselineEqual, equalWithoutDebugOrFrames, englishEqual;
        final List<String> baselineDifferences = new ArrayList<>();
        final List<String> englishDifferences = new ArrayList<>();
    }

    private static Map<String, byte[]> readJar(Path filename) throws IOException {
        Map<String, byte[]> output = new LinkedHashMap<>();
        try (ZipFile file = new ZipFile(filename.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                String name = entry.getName().substring(0, entry.getName().length() - 6);
                try (InputStream input = file.getInputStream(entry)) {
                    if (output.put(name, input.readAllBytes()) != null) {
                        throw new IOException("Duplicate class: " + entry.getName());
                    }
                }
            }
        }
        return output;
    }

    private static int u2(byte[] data, int offset) {
        return ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
    }

    private static int constantPoolEnd(byte[] bytes) {
        int count = u2(bytes, 8);
        int position = 10;
        for (int index = 1; index < count; index++) {
            int tag = bytes[position++] & 255;
            switch (tag) {
                case 1:
                    int length = u2(bytes, position);
                    position += 2 + length;
                    break;
                case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18:
                    position += 4;
                    break;
                case 5: case 6:
                    position += 8;
                    index++;
                    break;
                case 7: case 8: case 16: case 19: case 20:
                    position += 2;
                    break;
                case 15:
                    position += 3;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected constant-pool tag: " + tag);
            }
            if (position > bytes.length) throw new IllegalArgumentException("Truncated class constant pool");
        }
        return position;
    }

    // The upstream obfuscator constructs ClassWriter(reader, 0), which copies
    // the input constant pool before adding randomized names. Prefer a unique
    // matching pool prefix; otherwise only use an unambiguous metadata match.
    private static boolean samePoolPrefix(byte[] baseline, byte[] original) {
        int end = constantPoolEnd(baseline);
        if (end > constantPoolEnd(original)) return false;
        for (int index = 10; index < end; index++) {
            if (baseline[index] != original[index]) return false;
        }
        return true;
    }

    private static byte[] canonical(byte[] bytes, Remapper remapper, int flags) {
        ClassReader reader = new ClassReader(bytes);
        // A fresh writer drops the copied, unused old constants and rebuilds a
        // pool from visited content; both inputs go through this same process.
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, remapper), flags);
        return writer.toByteArray();
    }

    static Result analyze(Path baselineFile, Path englishFile, Path originalFile) throws IOException {
        Map<String, byte[]> baseline = readJar(baselineFile);
        Map<String, byte[]> english = readJar(englishFile);
        Map<String, byte[]> original = readJar(originalFile);
        Map<String, byte[]> product = new HashMap<>();
        Map<String, Metadata> shapes = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : baseline.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith("gg/vape/") && !name.startsWith("gg/vape/shaded/")
                    && !WHITELIST.contains(name)) {
                product.put(name, entry.getValue());
                shapes.put(name, new Metadata(entry.getValue()));
            }
        }

        Map<String, Integer> roots = new HashMap<>();
        for (String name : original.keySet()) {
            int slash = name.indexOf('/');
            if (slash > 0) {
                String root = name.substring(0, slash);
                if (root.matches("[a-z][a-z0-9]{15}")) roots.merge(root, 1, Integer::sum);
            }
        }
        String prefix = null;
        int randomCount = 0;
        boolean tied = false;
        for (Map.Entry<String, Integer> entry : roots.entrySet()) {
            if (entry.getValue() > randomCount) {
                prefix = entry.getKey();
                randomCount = entry.getValue();
                tied = false;
            } else if (entry.getValue() == randomCount) {
                tied = true;
            }
        }
        if (prefix == null || tied) throw new IllegalArgumentException("Cannot identify a unique randomized package: " + roots);

        Map<String, String> inverse = new HashMap<>();
        Map<String, String> forward = new HashMap<>();
        Result result = new Result();
        result.randomPrefix = prefix;
        result.randomCount = randomCount;
        result.productCount = product.size();
        for (Map.Entry<String, byte[]> entry : original.entrySet()) {
            if (!entry.getKey().startsWith(prefix + "/")) continue;
            byte[] bytes = entry.getValue();
            Metadata actual = new Metadata(bytes);
            Matcher matcher = OLD_NAME.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
            Set<String> candidates = new HashSet<>();
            while (matcher.find()) {
                String name = matcher.group();
                Metadata expected = shapes.get(name);
                if (expected != null && expected.shapeEquals(actual)) candidates.add(name);
            }
            List<String> exactPools = new ArrayList<>();
            for (String name : candidates) {
                if (samePoolPrefix(product.get(name), bytes)) exactPools.add(name);
            }
            String selected = exactPools.size() == 1 ? exactPools.get(0)
                    : candidates.size() == 1 ? candidates.iterator().next() : null;
            if (selected == null) {
                if (candidates.isEmpty()) result.unmatched++; else result.ambiguous++;
                continue;
            }
            if (forward.containsKey(selected)) {
                result.ambiguous++;
                continue;
            }
            if (exactPools.size() == 1) result.poolMatches++;
            forward.put(selected, entry.getKey());
            inverse.put(entry.getKey(), selected);
        }
        result.mappedProduct = forward.size();
        final String randomPrefix = prefix;
        Remapper reverse = new Remapper() {
            @Override
            public String map(String name) {
                return inverse.getOrDefault(name, name);
            }

            @Override
            public Object mapValue(Object value) {
                if (value instanceof String) {
                    String text = (String)value;
                    if (inverse.containsKey(text)) return inverse.get(text);
                    if (text.equals(randomPrefix + ".")) return "gg.vape.";
                    if (text.equals(randomPrefix + "/")) return "gg/vape/";
                }
                return super.mapValue(value);
            }
        };
        Remapper identity = new Remapper() {};
        for (Map.Entry<String, byte[]> entry : baseline.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith("gg/vape/module/")) continue;
            result.moduleCount++;
            String randomName = forward.get(name);
            if (randomName == null) {
                result.baselineDifferences.add(name + " (unmapped)");
                result.englishDifferences.add(name + " (unmapped)");
                continue;
            }
            result.mappedModules++;
            byte[] upstream = original.get(randomName);
            byte[] baseClass = entry.getValue();
            byte[] englishClass = english.get(name);
            byte[] normalizedUpstream = canonical(upstream, reverse, 0);
            if (Arrays.equals(canonical(baseClass, identity, 0), normalizedUpstream)) {
                result.baselineEqual++;
            } else {
                result.baselineDifferences.add(name);
            }
            int flags = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
            if (Arrays.equals(canonical(baseClass, identity, flags), canonical(upstream, reverse, flags))) {
                result.equalWithoutDebugOrFrames++;
            }
            if (englishClass != null && Arrays.equals(canonical(englishClass, identity, 0), normalizedUpstream)) {
                result.englishEqual++;
            } else {
                result.englishDifferences.add(name);
            }
        }
        Collections.sort(result.baselineDifferences);
        Collections.sort(result.englishDifferences);
        if (result.moduleCount == 0) throw new IllegalArgumentException("No baseline module classes found");
        return result;
    }

    static boolean matchesExpected(Result result, Set<String> expectedEnglishDifferences) {
        if (result.mappedProduct != result.randomCount || result.ambiguous != 0 || result.unmatched != 0
                || result.mappedModules != result.moduleCount || result.baselineEqual != result.moduleCount) {
            return false;
        }
        Set<String> actual = new HashSet<>(result.englishDifferences);
        if (!actual.containsAll(expectedEnglishDifferences)) return false;
        for (String name : actual) {
            if (expectedEnglishDifferences.contains(name)) continue;
            boolean reviewedNestedClass = false;
            for (String parent : expectedEnglishDifferences) {
                if (name.startsWith(parent + "$")) {
                    reviewedNestedClass = true;
                    break;
                }
            }
            if (!reviewedNestedClass) return false;
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 && (args.length != 5 || !"--embedded".equals(args[4]))) {
            throw new IllegalArgumentException("Expected: baseline.jar english.jar randomized.jar report.txt [--embedded]");
        }
        boolean embeddedMode = args.length == 5;
        Result result = analyze(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        String subject = embeddedMode ? "Embedded English payload" : "Original obfuscated release";
        String baselineLabel = embeddedMode ? "English source build" : "baseline";
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(subject + " comparison (offline, after reversing randomized class names):");
        lines.add("Randomized classes mapped: " + result.mappedProduct + "/" + result.randomCount
                + "; exact copied constant pools: " + result.poolMatches + "; ambiguous: "
                + result.ambiguous + "; unmatched: " + result.unmatched + ".");
        lines.add("Module classes mapped: " + result.mappedModules + "/" + result.moduleCount + ".");
        lines.add("Normalized " + subject + " vs " + baselineLabel + ": " + result.baselineEqual + "/"
                + result.moduleCount + " module class files identical (includes code, class members and debug data).");
        lines.add("Same comparison with debug and stack-map frames omitted: "
                + result.equalWithoutDebugOrFrames + "/" + result.moduleCount + ".");
        if (!embeddedMode) {
            lines.add("Normalized " + subject + " vs English source build: " + result.englishEqual + "/"
                    + result.moduleCount + " module class files identical (includes code, class members and debug data).");
        }
        if (!result.baselineDifferences.isEmpty()) {
            lines.add(subject + " vs " + baselineLabel + " differences or unmapped classes:");
            for (String name : result.baselineDifferences) lines.add("  " + name);
        }
        if (!embeddedMode && !result.englishDifferences.isEmpty()) {
            lines.add(subject + " vs English differences or unmapped classes:");
            for (String name : result.englishDifferences) lines.add("  " + name);
        }
        lines.add("Normalization removes randomized product class names and unused constants; it does not run the code.");
        lines.add("A normalized class match is not proof of identical in-game behavior or native dependencies.");
        lines.add("");
        Files.write(Path.of(args[3]), lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        for (String line : lines) System.out.println(line);
        String noticeTitle = embeddedMode ? "Normalized embedded module comparison" : "Normalized original module comparison";
        String notice = "Mapped " + result.mappedModules + "/" + result.moduleCount + "; "
                + subject + " vs " + baselineLabel + " " + result.baselineEqual + "/" + result.moduleCount;
        if (!embeddedMode) notice += "; vs English source build " + result.englishEqual + "/" + result.moduleCount;
        System.out.println("::notice title=" + noticeTitle + "::" + notice + ".");
        System.out.println("::notice title=" + subject + " class mapping::Mapped " + result.mappedProduct + "/"
                + result.randomCount + " randomized product classes; " + result.poolMatches
                + " copied constant-pool prefixes match; " + result.ambiguous + " ambiguous, "
                + result.unmatched + " unmatched.");
        Set<String> expected = embeddedMode ? Collections.emptySet() : EXPECTED_ENGLISH_MODULE_CHANGES;
        if (!matchesExpected(result, expected)) {
            System.out.println("::error title=Normalized module parity::" + subject + " module mapping or "
                    + "class differences are not the expected set; see the report.");
            System.exit(1);
        }
    }
}
