// Author: Othmane

package web.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only view of the CVRPLIB dataset under {@code Algorithm/CVRPLib}:
 * folder and instance listing, instance path resolution, and the known-optimal
 * cost published alongside an instance.
 *
 * @author Othmane EL YAAKOUBI
 */
final class Instances {

    private static final File DIR = new File("Algorithm/CVRPLib");
    private static final String VRP_EXT = ".vrp";
    private static final String[] SOLUTION_EXTS = {".sol", ".opt.sol", ".bst.sol"};

    private Instances() {
        // Static accessors only.
    }

    /**
     * Lists the CVRPLIB subdirectories.
     *
     * @return the sorted folder names, empty when the dataset is absent
     */
    static List<String> folders() {
        File[] dirs = DIR.listFiles(File::isDirectory);
        return dirs == null ? List.of()
                : Arrays.stream(dirs).map(File::getName).sorted().collect(Collectors.toList());
    }

    /**
     * Lists the {@code .vrp} instances of a folder, without the extension.
     *
     * @param folder the folder name, as received from the client
     * @return the sorted instance names, empty when the folder is unknown
     */
    static List<String> in(String folder) {
        String safe = safeName(folder);
        if (safe == null)
            return List.of();
        String[] files = new File(DIR, safe).list((d, n) -> n.endsWith(VRP_EXT));
        return files == null ? List.of()
                : Arrays.stream(files).map(n -> n.substring(0, n.length() - VRP_EXT.length()))
                        .sorted().collect(Collectors.toList());
    }

    /**
     * Resolves the {@code folder} and {@code file} query parameters into a
     * {@code .vrp} file.
     *
     * @param query the parsed query parameters
     * @return the instance file, or {@code null} when the parameters are missing or unsafe
     */
    static File vrpFile(Map<String, String> query) {
        String folder = safeName(query.get("folder"));
        String file = safeName(query.get("file"));
        if (folder == null || file == null)
            return null;
        return new File(new File(DIR, folder), file + VRP_EXT);
    }

    /**
     * Reads the known-optimal cost from the {@code .sol}, {@code .opt.sol}, or
     * {@code .bst.sol} sibling of the instance.
     *
     * @param vrp the instance file
     * @return the optimal cost, or {@code NaN} when no sibling publishes one
     */
    static double optimalOf(File vrp) {
        String base = vrp.getPath().replaceFirst("\\.vrp$", "");
        for (String ext : SOLUTION_EXTS) {
            File sol = new File(base + ext);
            if (!sol.exists()) continue;
            try {
                for (String line : Files.readAllLines(sol.toPath())) {
                    String t = line.trim();
                    if (t.startsWith("Cost")) {
                        String[] parts = t.split("\\s+");
                        if (parts.length == 2) return Double.parseDouble(parts[1]);
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
                // Try the next candidate file.
            }
        }
        return Double.NaN;
    }

    /** Trust boundary: reject anything but a plain file/dir name (no traversal). */
    private static String safeName(String name) {
        if (name == null || name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        return name;
    }
}
