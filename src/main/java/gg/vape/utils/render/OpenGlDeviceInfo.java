package gg.vape.utils.render;

import gg.vape.Vape;
import gg.vape.utils.render.GpuVendor;
import org.lwjgl.opengl.GL11;

public class OpenGlDeviceInfo {
    public static String vendorName;
    public static GpuVendor gpuVendor;
    private static int legacyValue;
    public static String rendererName;
    public static String versionName;

    public static int getLegacyConstant() {
        int ignoredLegacyValue = OpenGlDeviceInfo.getLegacyValue();
        return 112;
    }

    public static int getLegacyValue() {
        return legacyValue;
    }

    public static void collectDeviceInfo() {
        try {
            String renderer = GL11.glGetString((int)7937);
            String vendor = GL11.glGetString((int)7936);
            String version = GL11.glGetString((int)7938);
            vendorName = vendor != null ? vendor : "Unknown Vendor";
            rendererName = renderer != null ? renderer : "Unknown GPU";
            versionName = version != null ? version : "Unknown Version";
            gpuVendor = OpenGlDeviceInfo.detectGpuVendor(vendor);
        }
        catch (Exception exception) {
            Vape.debugLog("Error getting OpenGL: " + exception.getMessage());
        }
    }

    public static void setLegacyValue(int value) {
        legacyValue = value;
    }

    static {
        OpenGlDeviceInfo.setLegacyValue(0);
        gpuVendor = GpuVendor.UNKNOWN;
        vendorName = null;
        versionName = null;
        rendererName = null;
    }

    private static Exception propagateException(Exception exception) {
        return exception;
    }

    private static GpuVendor detectGpuVendor(String vendorName) {
        if (vendorName == null) {
            return GpuVendor.UNKNOWN;
        }
        switch (vendorName) {
            case "NVIDIA Corporation": {
                return GpuVendor.NVIDIA;
            }
            case "Intel": 
            case "Intel Open Source Technology Center": {
                return GpuVendor.INTEL;
            }
            case "AMD": 
            case "ATI Technologies Inc.": {
                return GpuVendor.AMD;
            }
        }
        return GpuVendor.UNKNOWN;
    }

    public static void appendDeviceInfo(StringBuilder output) {
        OpenGlDeviceInfo.collectDeviceInfo();
        output.append("GPU vendor: ").append(gpuVendor.name()).append(" (").append(vendorName).append(")\n");
        output.append("GPU renderer: ").append(rendererName).append('\n');
        output.append("OpenGL version: ").append(versionName).append('\n');
    }

    private static void logCapabilities() {
        try {
            Vape.debugLog("MAX_TEXTURE_SIZE - " + GL11.glGetInteger((int)3379));
            Vape.debugLog("MAX_TEXTURE_UNITS - " + GL11.glGetInteger((int)34930));
            Vape.debugLog("MAX_VERTEX_ATTRIBS - " + GL11.glGetInteger((int)34921));
            Vape.debugLog("MAX_COLOR_ATTACHMENTS - " + GL11.glGetInteger((int)36063));
            Vape.debugLog("MAX_VIEWPORT_WIDTH - " + GL11.glGetInteger((int)3386));
            Vape.debugLog("MAX_VERTEX_UNIFORM_COMPONENTS - " + GL11.glGetInteger((int)35658));
            Vape.debugLog("MAX_FRAGMENT_UNIFORM_COMPONENTS - " + GL11.glGetInteger((int)35657));
        }
        catch (Exception exception) {
            Vape.debugLog("Failed to collect GPU capabilities: " + exception.getMessage());
        }
    }

    public static void logDeviceInfo() {
        Vape.debugLog("===== Graphics Information =====");
        Vape.debugLog("Vendor: " + gpuVendor.name() + " (" + vendorName + ")");
        Vape.debugLog("Device Name: " + rendererName);
        Vape.debugLog("Driver Version: " + versionName);
        Vape.debugLog("---GPU Capabilities---");
        OpenGlDeviceInfo.logCapabilities();
        Vape.debugLog("================================");
    }
}
