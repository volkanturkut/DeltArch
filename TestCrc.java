import java.io.*;
import java.util.zip.*;

public class TestCrc {
    public static void main(String[] args) throws Exception {
        File f1 = new File("test.nes");
        try (FileOutputStream fos = new FileOutputStream(f1)) {
            fos.write(new byte[8192]);
        }

        long crcStream;
        try (InputStream input = new FileInputStream(f1)) {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                crc.update(buffer, 0, length);
            }
            crcStream = crc.getValue();
        }
        System.out.println("stream crc: " + crcStream);

        File zipFile = new File("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry = new ZipEntry("test.nes");
            zos.putNextEntry(entry);
            zos.write(new byte[8192]);
            zos.closeEntry();
        }

        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry("test.nes");
            System.out.println("zip crc: " + entry.getCrc());
        }
    }
}
