import java.io.File
import java.util.zip.CRC32

fun main() {
    val f1 = File("test.nes")
    f1.writeBytes(ByteArray(8192) { 1 })

    val crc2 = CRC32()
    f1.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var length: Int
        while (input.read(buffer).also { length = it } >= 0) {
            crc2.update(buffer, 0, length)
        }
    }
    println("f1 stream crc: ${crc2.value}")
}
