//author: Kandia Brown
//date: 11/22/2026
//This is the product storage class. It stores and manages the product photos
package product;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Storage class from the SDS class diagram.
 * Keeps the product pictures in a folder, one sub-folder per product.
 */
public class Storage {

    private static final int MAX_IMAGES = 5;                                        // A-3
    private static final List<String> ALLOWED = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");

    private String storagePath;

    public Storage(String storagePath) {
        this.storagePath = storagePath;
        try {
            Files.createDirectories(Paths.get(storagePath));
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the storage folder: " + storagePath, e);
        }
    }

    /** Saves a picture for a product and returns the path it was stored at. */
    public String uploadImage(String productID, File image) {
        if (image == null || !image.isFile()) {
            throw new IllegalArgumentException("No image file was given.");
        }
        String extension = extensionOf(image.getName());
        if (!ALLOWED.contains(extension)) {
            throw new IllegalArgumentException("Only image files (jpg, jpeg, png, gif, webp) are allowed.");
        }
        if (retrieveImage(productID).size() >= MAX_IMAGES) {
            throw new IllegalStateException("A product can have at most " + MAX_IMAGES + " images.");
        }
        try {
            Path folder = Paths.get(storagePath, productID);
            Files.createDirectories(folder);
            Path target = folder.resolve(UUID.randomUUID() + "." + extension);
            Files.copy(image.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the image.", e);
        }
    }

    /** Returns the paths of all pictures stored for a product (empty if there are none). */
    public ArrayList<String> retrieveImage(String productID) {
        ArrayList<String> paths = new ArrayList<>();
        File[] files = Paths.get(storagePath, productID).toFile().listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                if (file.isFile()) {
                    paths.add(file.getPath());
                }
            }
        }
        return paths;
    }

    /** Deletes one stored picture. Returns false if there was nothing to delete. */
    public boolean deleteImage(String imagePath) {
        try {
            return Files.deleteIfExists(Paths.get(imagePath));
        } catch (IOException e) {
            return false;
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}