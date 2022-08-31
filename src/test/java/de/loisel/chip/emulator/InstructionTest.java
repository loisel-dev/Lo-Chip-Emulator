/*
 * Copyright 2022 Elias Taufer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.loisel.chip.emulator;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstructionTest {

    private final LoChip chip;
    private final Keyboard keyboard;
    private final FrameBuffer frameBuffer;

    String binPath;

    InstructionTest() {
        String path = "src/test/resources";

        File file = new File(path);
        binPath = file.getAbsolutePath() + File.separator + "bin" + File.separator;

        this.keyboard = new Keyboard();
        this.frameBuffer = new FrameBuffer(256, 144);

        this.chip = new LoChip(new Program(new byte[0]), frameBuffer, keyboard);
    }

    @Test
    void clearDisplay() {
        frameBuffer.setPixel(3, 3, (byte) 10);
        byte[] rom = {
                (byte) 0xE0, (byte) 0xAA
        };
        chip.loadProgram(new Program(rom));
        chip.run(2);
        byte[][] buff = frameBuffer.copyBBuffer();
        for (byte[] line: buff) {
            for (byte b : line) {
                assertEquals(0, b);
            }
        }
    }

    @Test
    void jmpAndSubroutine() {
        chip.loadProgram(new Program(binPath + "JmpSrt.bin"));

        chip.run(100);

        byte[] mem = chip.dumpMemory();
        assertEquals(0x7F, mem[0x230]);
    }

    @Test
    void millionInstTest() {
        chip.loadProgram(new Program(binPath + "MilliInst.bin"));

        chip.run(1000000);

        byte[] mem = chip.dumpMemory();
        assertEquals(0x7F, mem[0x230]);
    }
}
