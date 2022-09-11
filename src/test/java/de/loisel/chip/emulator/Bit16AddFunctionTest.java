package de.loisel.chip.emulator;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Bit16AddFunctionTest {

    private final LoChip chip;

    String binPath;

    Bit16AddFunctionTest() {
        String path = "src/test/resources";

        File file = new File(path);
        binPath = file.getAbsolutePath() + File.separator + "bin" + File.separator;

        Keyboard keyboard = new Keyboard();
        FrameBuffer frameBuffer = new FrameBuffer(256, 144);

        this.chip = new LoChip(new Program(new byte[0]), frameBuffer, keyboard);
    }

    @Test
    void simpleProgram() {
        chip.loadProgram(new Program(binPath + "16b-addFunction.bin"));

        chip.run(100);

        byte[] mem = chip.dumpMemory();

        for (int i = 0; i < mem.length; i++) {
            if (i % 16 == 0 && i > 0) {
                System.out.print("\n");
            }

            if (i % 16 == 0) {
                String address = HexFormat.of().toHexDigits(i).toUpperCase(Locale.ROOT);
                if(address.length() == 1)
                    address = "000" + address;
                else if(address.length() == 2)
                    address = "00" + address;
                else if(address.length() == 3)
                    address = "0" + address;
                address = address.substring(4);
                System.out.print("0x" + address + ":  ");
            }

            String number = HexFormat.of().formatHex(Arrays.copyOfRange(mem, i, i + 1)).toUpperCase();
            System.out.print(number + "  ");

        }
    }

}
