//author: Kandia Brown
//date: 11/22/2026
//Tests the product storage class (saving, retrieving and deleting product photos)
package product;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class StorageTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean result) {
        if (result) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name); }
    }

    public static void main(String[] args) throws Exception {

        Path folder = Files.createTempDirectory("peakfinds-storage");
        Storage storage = new Storage(folder.toString());
        File picture = Files.write(Files.createTempFile("picture", ".png"), new byte[] {1, 2, 3}).toFile();

        String savedPath = storage.uploadImage("P-001", picture);
        check("Storage: an uploaded picture is saved", new File(savedPath).isFile());
        check("Storage: the picture can be retrieved", storage.retrieveImage("P-001").size() == 1);

        File notAPicture = Files.write(Files.createTempFile("notes", ".txt"), new byte[] {1}).toFile();
        boolean rejected = false;
        try {
            storage.uploadImage("P-001", notAPicture);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check("Storage: a file that is not a picture is refused", rejected);

        for (int i = 0; i < 4; i++) {
            storage.uploadImage("P-001", picture);
        }
        boolean overLimit = false;
        try {
            storage.uploadImage("P-001", picture);
        } catch (IllegalStateException e) {
            overLimit = true;
        }
        check("Storage: a sixth picture is refused", overLimit);

        check("Storage: a picture can be deleted", storage.deleteImage(savedPath) && !new File(savedPath).exists());
        check("Storage: deleting a missing picture returns false", !storage.deleteImage(savedPath));
        check("Storage: a product with no pictures returns an empty list", storage.retrieveImage("P-999").isEmpty());

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed.");
    }
}
