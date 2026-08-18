package online.fujinet.go.adam

import online.fujinet.go.adam.settings.RomStore
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaImportClassifyTest {

    @Test
    fun canonicalDumpsClassifyByCrcRegardlessOfName() {
        for (rom in RomStore.AdamRom.entries) {
            assertEquals(
                MediaImport.RomImportResult.Success(rom.fileName, crcMatches = true),
                MediaImport.classifySystemRom(rom.expectedSize.toLong(), rom.expectedCrc, "whatever.bin"),
            )
        }
    }

    @Test
    fun nameHintFallbackAcceptsWithWarning() {
        // A personal re-dump with a different CRC but a telling file name.
        assertEquals(
            MediaImport.RomImportResult.Success("EOS.rom", crcMatches = false),
            MediaImport.classifySystemRom(8192L, 0xDEADBEEFL, "my-eos-dump.bin"),
        )
    }

    @Test
    fun eightKUnknownWithNoHintIsRejected() {
        // OS7 and EOS are both 8 KB; without a CRC or name match we must not
        // guess which slot to overwrite.
        assertEquals(
            MediaImport.RomImportResult.NotRecognized(8192L),
            MediaImport.classifySystemRom(8192L, 0xDEADBEEFL, "game.col"),
        )
    }

    @Test
    fun unknownSizeIsRejected() {
        assertEquals(
            MediaImport.RomImportResult.NotRecognized(16384L),
            MediaImport.classifySystemRom(16384L, RomStore.AdamRom.OS7.expectedCrc, "os7.rom"),
        )
    }
}
