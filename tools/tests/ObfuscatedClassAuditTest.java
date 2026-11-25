import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.commons.ClassRemapper;
import jdk.internal.org.objectweb.asm.commons.Remapper;

/** Self-contained tests, including a deliberately changed module body. */
public final class ObfuscatedClassAuditTest {
    private static byte[] makeClass(String name, String source, String value, String dependency) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitSource(source, null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "text", "()Ljava/lang/String;", null, null);
        method.visitCode();
        if (dependency != null) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, dependency, "text", "()Ljava/lang/String;", false);
            method.visitInsn(Opcodes.POP);
        }
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeJar(Path file, Map<String, byte[]> classes) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static Map<String, byte[]> obfuscate(Map<String, byte[]> baseline) {
        String prefix = "abcdefghijklmn12";
        Map<String, String> mapping = new HashMap<>();
        int index = 0;
        for (String name : baseline.keySet()) mapping.put(name, prefix + "/" + (++index) + "abcdefghijklmnop");
        Remapper remapper = new Remapper() {
            @Override public String map(String name) { return mapping.getOrDefault(name, name); }
            @Override public Object mapValue(Object value) {
                if (value instanceof String) {
                    String text = (String)value;
                    if (mapping.containsKey(text)) return mapping.get(text);
                    if (text.equals("gg.vape.")) return prefix + ".";
                    if (text.equals("gg/vape/")) return prefix + "/";
                }
                return super.mapValue(value);
            }
        };
        Map<String, byte[]> output = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : baseline.entrySet()) {
            ClassReader reader = new ClassReader(entry.getValue());
            // This reproduces the upstream obfuscator's constant-pool copying.
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassRemapper(writer, remapper), 0);
            output.put(mapping.get(entry.getKey()), writer.toByteArray());
        }
        return output;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("obfuscated-audit-test-");
        try {
            String first = "gg/vape/module/one/First";
            String nested = "gg/vape/module/one/First$1";
            String second = "gg/vape/module/two/Second";
            String helper = "gg/vape/common/Helper";
            Map<String, byte[]> baseline = new LinkedHashMap<>();
            baseline.put(first, makeClass(first, "First.java", "gg.vape.", helper));
            baseline.put(nested, makeClass(nested, "First.java", "nested", first));
            baseline.put(second, makeClass(second, "Second.java", "unchanged", first));
            baseline.put(helper, makeClass(helper, "Helper.java", "helper", null));
            Map<String, byte[]> english = new LinkedHashMap<>(baseline);
            english.put(second, makeClass(second, "Second.java", "translated", first));
            Path baseFile = directory.resolve("baseline.jar");
            Path engFile = directory.resolve("english.jar");
            Path origFile = directory.resolve("original.jar");
            writeJar(baseFile, baseline);
            writeJar(engFile, english);
            writeJar(origFile, obfuscate(baseline));
            ObfuscatedClassAudit.Result identical = ObfuscatedClassAudit.analyze(baseFile, engFile, origFile);
            check(identical.mappedProduct == 4 && identical.poolMatches == 4, "Product class mapping failed");
            check(identical.moduleCount == 3 && identical.mappedModules == 3, "Module inventory failed");
            check(identical.baselineEqual == 3 && identical.equalWithoutDebugOrFrames == 3, "Normalization failed");
            check(identical.englishEqual == 2 && identical.englishDifferences.contains(second), "English change not detected");
            check(ObfuscatedClassAudit.matchesExpected(identical, Set.of(second)), "Expected differences were rejected");
            check(!ObfuscatedClassAudit.matchesExpected(identical, Set.of(first)), "Unexpected differences passed");

            Map<String, byte[]> alteredOriginal = new LinkedHashMap<>(baseline);
            alteredOriginal.put(first, makeClass(first, "First.java", "different logic", helper));
            writeJar(origFile, obfuscate(alteredOriginal));
            ObfuscatedClassAudit.Result altered = ObfuscatedClassAudit.analyze(baseFile, engFile, origFile);
            check(altered.mappedModules == 3, "Changed module could not be identified");
            check(altered.baselineEqual == 2 && altered.baselineDifferences.contains(first), "Changed code was not detected");
            check(!ObfuscatedClassAudit.matchesExpected(altered, Set.of(second)), "Original code change passed the guard");
            System.out.println("Obfuscated class audit: mapping and changed-code tests passed");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException error) { throw new RuntimeException(error); }
                });
            }
        }
    }
}
