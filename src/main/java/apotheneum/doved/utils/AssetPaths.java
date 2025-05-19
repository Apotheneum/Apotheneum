package apotheneum.doved.utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AssetPaths {
  // Helper function to convert an absolute path to a relative path based on the
  // `Assets` directory
  public static String toRelativePathFromAssets(String absolutePath) {
    System.out.println("Converting absolute path from: " + absolutePath);
    File f_assets = assetsFolder();
    Path assetsPath = Paths.get(f_assets.getAbsolutePath());
    Path relativePath = assetsPath.relativize(Paths.get(absolutePath));
    String relativePathString = relativePath.toString();
    // replace windows slash with mac slash
    String asMacString = relativePathString.replace("\\", "/");
    System.out.println("Path converted to " + asMacString);
    return asMacString.toString();
  }

  // Helper function to convert a relative path (relative to the `Assets`
  // directory) to an absolute path
  public static String toAbsolutePathFromAssets(String relativePath) {
    System.out.println("Converting relative path from " + relativePath);
    if (isAbsolutePath(relativePath)) {
      return relativePath;
    }
    // Replace Unix forward slashes with the system's file separator
    String systemRelativePath = relativePath.replace("/", File.separator);
    File f_assets = assetsFolder();
    Path assetsPath = Paths.get(f_assets.getAbsolutePath());
    Path imageAbsolutePath = assetsPath.resolve(Paths.get(systemRelativePath)).normalize().toAbsolutePath();
    // Print debug statement
    System.out.println("to: " + imageAbsolutePath.toString());
    return imageAbsolutePath.toString();
  }

  private static File assetsFolder() {
    String assetsFromEnv = System.getenv("ASSETS_PATH");

    // if ASSETS_FOLDER is in the environment, use that
    if (assetsFromEnv != null && assetsFromEnv.length() > 0) {
      System.out.println("Using assets folder from environment: " + assetsFromEnv);
      return new File(assetsFromEnv + File.separator);
    }

    // Use ~/Chromatik/Assets as default
    String homeDir = System.getProperty("user.home");
    String defaultAssetsPath = homeDir + File.separator + "Chromatik" + File.separator + "Assets" + File.separator;
    System.out.println("Using default assets folder: " + defaultAssetsPath);
    return new File(defaultAssetsPath);
  }

  // Helper function to check if a given path is an absolute path
  public static boolean isAbsolutePath(String path) {
    return Paths.get(path).isAbsolute();
  }

  public static boolean isInAssetsFolder(String path) {
    File f_assets = assetsFolder();
    Path assetsPath = Paths.get(f_assets.getAbsolutePath());
    Path pathToCheck = Paths.get(path);
    return pathToCheck.startsWith(assetsPath);
  }

}
