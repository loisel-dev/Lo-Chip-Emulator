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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LoChip implements Runnable{
    Random rand;
    private boolean isRunning;
    private long remainInstr = Long.MAX_VALUE;
    private long cycleCount = 0;

    private byte rX;
    private byte rY;
    private boolean f;
    private short programCounter;
    private short indexReg;
    private int delayTimer;
    private int soundTimer;

    private final Memory memory;
    private final Stack stack;
    private final FrameBuffer frameBuffer;
    private final Keyboard keyboard;

    private final Map<Integer, Runnable> instructionMap;

    public LoChip(Program program, FrameBuffer frameBuffer, Keyboard keyboard) {
        this.frameBuffer = frameBuffer;
        this.keyboard = keyboard;
        this.memory = new Memory();
        this.stack = new Stack();

        this.programCounter = 0;
        this.indexReg = 0;
        this.delayTimer = 0;
        this.soundTimer = 0;
        this.rX = 0;
        this.rY = 0;

        instructionMap = new HashMap<>();
        setUpInstructionMap();

        this.isRunning = false;
        this.rand = new Random();

        loadProgram(program);
    }

    public synchronized void loadProgram(Program program) {
        byte[] rawProgram = program.getProgram();
        for (int i = programCounter; i < (programCounter + rawProgram.length); i++) {
            memory.write((short)i, rawProgram[i - programCounter]);
        }
        this.programCounter = memory.fetchWord((short) 0);
    }

    public synchronized boolean isSound() {
        return soundTimer > 0;
    }

    @Override
    public void run() {
        runProgram();
    }

    /**
     * @param instructions the max number of executed instructions
     * @return the number of instructions executed before exit
     */
    public long run(long instructions) {
        remainInstr = instructions;
        this.run();
        return  cycleCount;
    }

    public void debugRegs() {
        System.out.println("Registers:\n");

        System.out.println("Rx: " + rX);
        System.out.println("Ry: " + rY);
        System.out.println("f:  " + f);
        System.out.println("PC: " + programCounter);
        System.out.println("SP: " + stack.getStackPointer());
        System.out.println("I:  " + indexReg);
        System.out.println("DT: " + delayTimer);
        System.out.println("ST: " + soundTimer);
    }

    public byte[] dumpMemory() {
        return memory.copyData();
    }

    private void setUpInstructionMap() {
        instructionMap.put(0xE0,                // $E0 - CLS
                frameBuffer::clearBuffer
        );
        instructionMap.put(0xEE, () ->          // $EE - RET
                programCounter = stack.pop()
        );
        instructionMap.put(0x10, () ->          // $10 - JP addr
                programCounter = fetchPCWord()
        );
        instructionMap.put(0x20, () -> {        // $20 - CALL addr
            short dest = fetchPCWord();
            stack.push(programCounter);
            programCounter = dest;
        });
        instructionMap.put(0x30, () -> {        // $30 - SE Rx, b1
            byte b1 = fetchPC();
            if(rX == b1)
                programCounter = indexReg;
        });
        instructionMap.put(0x31, () -> {        // $31 - JNE Rx, b1
            byte b1 = fetchPC();
            if(rX != b1)
                programCounter = indexReg;
        });
        instructionMap.put(0x50, () -> {        // $50 - JE Rx, Ry
            if(rX == rY)
                programCounter = indexReg;
        });
        instructionMap.put(0x51, () -> {        // $51 - JNE Rx, Ry
            if(rX != rY)
                programCounter = indexReg;
        });
        instructionMap.put(0x60, () ->          // $60 - LD Rx, b1
                rX = fetchPC()
        );
        instructionMap.put(0x61, () ->          // $61 - LD Ry, b1
                rY = fetchPC()
        );


        instructionMap.put(0x62, () ->          // $62 - LDM Rx, I
                rX = memory.fetch(indexReg)
        );
        instructionMap.put(0x63, () ->          // $63 - LDM Ry, I
                rY = memory.fetch(indexReg)
        );
        instructionMap.put(0x64, () ->          // $64 - LDM I, Rx
                memory.write(indexReg, rX)
        );
        instructionMap.put(0x65, () ->          // $65 - LDM I, Ry
                memory.write(indexReg, rY)
        );


        instructionMap.put(0x70, () ->          // $70 - ADD Rx, b1
                rX += fetchPC()
        );
        instructionMap.put(0x71, () ->          // $71 - ADD Ry, b1
                rY += fetchPC()
        );
        instructionMap.put(0x80, () ->          // $80 - LD Rx, [Ry]
                rX = rY
        );
        instructionMap.put(0x8A, () ->          // $8A - LD Ry, [Rx]
                rY = rX
        );
        instructionMap.put(0x81, () ->          // $81 - OR Rx, Ry
                rX |= rY
        );
        instructionMap.put(0x82, () ->          // $82 - AND Rx, Ry
                rX &= rY
        );
        instructionMap.put(0x83, () ->          // $83 - XOR Rx, Ry
                rX ^= rY
        );
        instructionMap.put(0x84, () -> {        // $84 - ADD Rx, Ry
                f = willAdditionOverflow(rX, rY);
                rX += rY;
        });
        instructionMap.put(0x85, () -> {        // $85 - SUB Rx, Ry
            f = willSubtractionOverflow(rX, rY);
            rX -= rY;
        });
        instructionMap.put(0x86, () -> {        // $86 - SHR Rx, b1
            f = (0b00000001 & rX) != 0;
            rX = (byte) ((rX & 0xFF) >>> 1);
        });
        instructionMap.put(0x87, () -> {        // $87 - SUBN Rx, Ry
            f = willSubtractionOverflow(rY, rX);
            rX = (byte) (rY - rX);
        });
        instructionMap.put(0x8E, () -> {        // $8E - SHL Rx, 1
            f = (0b10000000 & rX) != 0;
            rX = (byte) (rX << 1);
        });
        instructionMap.put(0xA0, () ->          // $A0 - LD I, addr
                indexReg = fetchPCWord()
        );
        instructionMap.put(0xA1, () -> {        // $A1 - LD I, [RxRy]
            indexReg = (short) (rX << 8);
            indexReg |= rY;
        });
        instructionMap.put(0xB0, () ->          // $B0 - JP Rx, addr
                programCounter = (short) (fetchPC() + rX)
        );
        instructionMap.put(0xC0, () ->          // $C0 - RND Rx, b1
                rX = (byte) (rand.nextInt() & fetchPC())
        );
        instructionMap.put(0xD0, () -> {        // $D0 - DRW Rx, Ry, n
            int b1 = Byte.toUnsignedInt(fetchPC());
            byte[] sprite = memory.fetchArray(indexReg, b1);
            f = frameBuffer.setSprite(sprite, rX, rY);
        });
        instructionMap.put(0xE1, () -> {        // $E1 - JKP Rx
            if(keyboard.isDown(rX))
                programCounter = indexReg;
        });
        instructionMap.put(0xE2, () -> {        // $E2 - JKNP Rx
            if(!keyboard.isDown(rX))
                programCounter = indexReg;
        });
        instructionMap.put(0xF1, () ->          // $F1 - LD Rx, DT
                rX = (byte) delayTimer
        );
        instructionMap.put(0xF2, () -> {        // $F2 - LD Rx, K
            byte k;
            do {
                k = keyboard.getNextKey();
            } while (k == 0xFF);
            rX = k;
        });
        instructionMap.put(0xF3, () ->          // $F3 - LD DT, Rx
                delayTimer = rX
        );
        instructionMap.put(0xF4, () ->          // $F4 - LD ST, Rx
                soundTimer = rX
        );
        instructionMap.put(0xFA, () ->          // $FA - ADD I, Rx
                indexReg += rX
        );
        instructionMap.put(0xFC, () -> {        // $FC - LD B, Rx
            int num = Byte.toUnsignedInt(rX);
            memory.write(indexReg, (byte) (num / 100));
            memory.write((short) (indexReg + 1), (byte) ((num % 100) / 10));
            memory.write((short) (indexReg + 2), (byte) ((num % 100) % 10));
        });
        instructionMap.put(0xFD, () -> {        // $FD - LD [I], Rx, Ry
            memory.write(indexReg, rX);
            memory.write((short) (indexReg + 1), rY);
        });
        instructionMap.put(0xFE, () -> {        // $FE - LD Rx, Ry, [I]
            rX = memory.fetch(indexReg);
            rY = memory.fetch((short) (indexReg + 1));
        });
        instructionMap.put(0xD1, () -> {        // $D1 - DRW Rx, Ry
            // Instruction not implemented
            // Display 16x16 sprite
        });
        instructionMap.put(0xAA, () ->          // $AA - EXIT
                isRunning = false
        );
    }
    private void runProgram() {
        this.loop();
    }

    private void loop() {
        System.out.println("START LOOP =======================");

        long lastTimerUpdate = System.nanoTime();
        isRunning = true;

        /* debug info */
        cycleCount = 0;
        long startTime = System.currentTimeMillis();
        /* debug info */

        while(isRunning && remainInstr > 0) {
            long currentTime = System.nanoTime();

            cycle();
            cycleCount++;

            if(currentTime - lastTimerUpdate >= (1e9F/60L)) {
                if(delayTimer > 0)
                    delayTimer--;
                if(soundTimer > 0)
                    soundTimer --;
                lastTimerUpdate = currentTime;
            }

            remainInstr--;
        }
        long endT = System.currentTimeMillis();
        System.out.println("Program ran " + (endT - startTime) + " milliseconds");
        System.out.println("Executed " + cycleCount + " cycles");
        System.out.println("Average of " + (cycleCount / endT) + " instructions per millisecond");
        System.out.println("END LOOP =========================\n");
    }

    private void cycle() {
        // Fetch
        int opcode = Byte.toUnsignedInt(fetchPC());

        Runnable method = instructionMap.get(opcode);
        if(method != null)
            method.run();
        else
            System.out.println("Opcode " + Integer.toHexString(opcode) + " not found!");
    }

    private byte fetchPC() {
        byte data = memory.fetch(programCounter);
        programCounter++;
        return data;
    }

    private short fetchPCWord() {
        short data = (short)(memory.fetch(programCounter) << 8);
        programCounter++;
        data |= memory.fetch(programCounter);
        programCounter++;
        return data;
    }

    private static boolean willAdditionOverflow(byte left, byte right) {
        int l = Byte.toUnsignedInt(left);
        int r = Byte.toUnsignedInt(right);
        return l + r > 255;
    }

    private static boolean willSubtractionOverflow(byte left, byte right) {
        int l = Byte.toUnsignedInt(left);
        int r = Byte.toUnsignedInt(right);
        return r < l;
    }

}
