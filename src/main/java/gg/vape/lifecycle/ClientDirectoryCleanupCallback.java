package gg.vape.lifecycle;

import gg.vape.config.LocalConfigStore;
import java.io.File;

public class ClientDirectoryCleanupCallback
implements ClientLifecycleCallback {
    @Override
    public void log(String message) {
    }

    public ClientDirectoryCleanupCallback() {
        File clientDirectory = LocalConfigStore.baseDirectory();
        if (clientDirectory.exists()) {
            File[] children = clientDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    String name = child.getName();
                    if (name.equals("cache")
                            || name.equals("config.json")
                            || name.equals("log")
                            // 用户脚本和脚本持久化数据必须保留，否则每次启动都会把
                            // .vapeclient/scripts 整个删掉（示例脚本会被重建，看起来像正常）。
                            || name.equals("scripts")) {
                        continue;
                    }
                    deleteRecursively(child);
                }
            }
        }
    }

    // File.delete() cannot remove a non-empty directory, so the recovery
    // directory (with its per-pid DLL/JAR artifacts) must be walked first.
    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }


    @Override
    public void close() {
    }
}
