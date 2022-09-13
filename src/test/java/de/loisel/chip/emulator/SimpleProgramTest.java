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

class SimpleProgramTest {

    private final LoChip chip;

    String binPath;

    SimpleProgramTest() {
        String path = "src/test/resources";

        File file = new File(path);
        binPath = file.getAbsolutePath() + File.separator + "bin" + File.separator;

        this.chip = new LoChip(new Program(new byte[0]));
    }

    @Test
    void simpleProgram() {
        chip.loadProgram(new Program(binPath + "simple-test.bin"));

        chip.run(100);

        chip.logMemory();

        byte[] mem = chip.dumpMemory();
        assertEquals(0x01, mem[0x0022]);
        assertEquals(0x02, mem[0x0023]);
    }

}
