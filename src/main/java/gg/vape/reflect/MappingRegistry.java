package gg.vape.reflect;

import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.runtime.NativeBridge;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MappingRegistry {
    public static final Map<String, Set<String>> METHODS = new HashMap<String, Set<String>>();
    public static final Map<String, Set<String>> METHODS_REVERSED = new HashMap<String, Set<String>>();
    public static final Map<String, Set<String>> FIELDS = new HashMap<String, Set<String>>();
    public static final Map<String, Set<String>> FIELDS_REVERSED = new HashMap<String, Set<String>>();
    private static final String RESOURCE_DIRECTORY = getResourceDirectory();
    private static final String METHODS_RESOURCE = RESOURCE_DIRECTORY + "/methods.csv";
    private static final String FIELDS_RESOURCE = RESOURCE_DIRECTORY + "/fields.csv";
    private static final int VANILLA_MAPPING_VERSION =
            NativeBridge.isForgeAbsent() ? ForgeVersion.c() : 0;
    private static final boolean FABRIC_12111_RUNTIME =
            VANILLA_MAPPING_VERSION == 61
                    && Fabric12111Mappings.isRuntimePresent();
    private static final boolean FABRIC_12110_RUNTIME =
            VANILLA_MAPPING_VERSION == 60
                    && Fabric12110Mappings.isRuntimePresent();

    private MappingRegistry() {
    }

    private static String getResourceDirectory() {
        switch (ForgeVersion.c()) {
            case 13:
                return "mappings/forge1710";
            case 15:
                return "mappings/forge189";
            case 23:
                return "mappings/forge1122";
            case 35:
                return "mappings/forge1164";
            case 36:
                return "mappings/forge1165";
            case 47:
                return "mappings/forge1201";
            case 52:
                return "mappings/forge1211";
            case 50:
                return "mappings/forge1206";
            case 61:
                return "mappings/forge12111";
            case 60:
                return "mappings/forge12110";
            default:
                return "mappings/forge189";
        }
    }

    public static void parseCsv(InputStream stream, Map<String, Set<String>> namesToSrg, Map<String, Set<String>> srgToNames) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream));){
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                String srgName = columns[0];
                String readableName = columns[1];
                namesToSrg.computeIfAbsent(readableName, ignored -> new HashSet()).add(srgName);
                srgToNames.computeIfAbsent(srgName, ignored -> new HashSet()).add(readableName);
            }
        }
    }

    public static boolean matches(Method method, String requestedName) {
        if (MappingRegistry.matches(
                method.getName(), requestedName, METHODS, METHODS_REVERSED)) {
            return true;
        }
        String srgName = lookupVanillaMethodSrgName(method);
        return srgName != null && MappingRegistry.matches(
                srgName, requestedName, METHODS, METHODS_REVERSED);
    }

    public static boolean matches(Field field, String requestedName) {
        if (MappingRegistry.matches(
                field.getName(), requestedName, FIELDS, FIELDS_REVERSED)) {
            return true;
        }
        String srgName = lookupVanillaFieldSrgName(field);
        return srgName != null && MappingRegistry.matches(
                srgName, requestedName, FIELDS, FIELDS_REVERSED);
    }

    private static boolean matches(String actualName, String requestedName, Map<String, Set<String>> namesToSrg, Map<String, Set<String>> srgToNames) {
        return actualName.equals(requestedName) || namesToSrg.getOrDefault(requestedName, Collections.emptySet()).contains(actualName) || srgToNames.getOrDefault(requestedName, Collections.emptySet()).contains(actualName);
    }

    private static String lookupVanillaMethodSrgName(Method method) {
        if (VANILLA_MAPPING_VERSION == 13) {
            return Vanilla1710Mappings.lookupMethodSrgName(method);
        }
        if (VANILLA_MAPPING_VERSION == 15) {
            return Vanilla189Mappings.lookupMethodSrgName(method);
        }
        if (VANILLA_MAPPING_VERSION == 23) {
            return Vanilla1122Mappings.lookupMethodSrgName(method);
        }
        if (VANILLA_MAPPING_VERSION == 35 || VANILLA_MAPPING_VERSION == 36) {
            return Vanilla1165Mappings.lookupMethodSrgName(method);
        }
        if (VANILLA_MAPPING_VERSION == 47) {
            return Vanilla1201Mappings.lookupMethodSrgName(method);
        }
        if (VANILLA_MAPPING_VERSION == 52) {
            return Vanilla1211Mappings.lookupMethodSrgName(method);
        }
        if (VANILLA_MAPPING_VERSION == 50) {
            return Vanilla1206Mappings.lookupMethodSrgName(method);
        }
        if (VANILLA_MAPPING_VERSION == 61) {
            if (FABRIC_12111_RUNTIME) {
                return Fabric12111Mappings.lookupMethodSrgName(method);
            }
            return Vanilla12111Mappings.lookupMethodSrgName(method);
        }
        if (VANILLA_MAPPING_VERSION == 60) {
            if (FABRIC_12110_RUNTIME) {
                return Fabric12110Mappings.lookupMethodSrgName(method);
            }
            return Vanilla12110Mappings.lookupMethodSrgName(method);
        }
        return null;
    }

    private static String lookupVanillaFieldSrgName(Field field) {
        if (VANILLA_MAPPING_VERSION == 13) {
            return Vanilla1710Mappings.lookupFieldSrgName(field);
        }
        if (VANILLA_MAPPING_VERSION == 15) {
            return Vanilla189Mappings.lookupFieldSrgName(field);
        }
        if (VANILLA_MAPPING_VERSION == 23) {
            return Vanilla1122Mappings.lookupFieldSrgName(field);
        }
        if (VANILLA_MAPPING_VERSION == 35 || VANILLA_MAPPING_VERSION == 36) {
            return Vanilla1165Mappings.lookupFieldSrgName(field);
        }
        if (VANILLA_MAPPING_VERSION == 47) {
            return Vanilla1201Mappings.lookupFieldSrgName(field);
        }
        if (VANILLA_MAPPING_VERSION == 52) {
            return Vanilla1211Mappings.lookupFieldSrgName(field);
        }
        if (VANILLA_MAPPING_VERSION == 50) {
            return Vanilla1206Mappings.lookupFieldSrgName(field);
        }
        if (VANILLA_MAPPING_VERSION == 61) {
            if (FABRIC_12111_RUNTIME) {
                return Fabric12111Mappings.lookupFieldSrgName(field);
            }
            return Vanilla12111Mappings.lookupFieldSrgName(field);
        }
        if (VANILLA_MAPPING_VERSION == 60) {
            if (FABRIC_12110_RUNTIME) {
                return Fabric12110Mappings.lookupFieldSrgName(field);
            }
            return Vanilla12110Mappings.lookupFieldSrgName(field);
        }
        return null;
    }

    private static InputStream openRequiredResource(String resourcePath) throws IOException {
        InputStream stream = MappingRegistry.class.getResourceAsStream('/' + resourcePath);
        if (stream == null) {
            throw new FileNotFoundException("Missing mapping resource: " + resourcePath);
        }
        return stream;
    }

    static {
        try {
            MappingRegistry.parseCsv(MappingRegistry.openRequiredResource(METHODS_RESOURCE), METHODS, METHODS_REVERSED);
            MappingRegistry.parseCsv(MappingRegistry.openRequiredResource(FIELDS_RESOURCE), FIELDS, FIELDS_REVERSED);
        }
        catch (Throwable failure) {
            failure.printStackTrace();
            throw new RuntimeException();
        }
    }
}
