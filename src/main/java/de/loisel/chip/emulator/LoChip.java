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

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Random;

public class LoChip implements Runnable{
    Random rand;
    private boolean isRunning;
    private long remainInstr = Long.MAX_VALUE;
    private long cycleCount = 0;

    private short X;
    private short Y;
    private short A;
    private short I;
    private short PC;
    private boolean C;
    private boolean N;
    private boolean Z;

    private final Memory memory;
    private final Stack stack;

    private final Runnable[] instructions;

    public LoChip(Program program) {
        this.memory = new Memory();
        this.stack = new Stack();

        this.X = 0;
        this.Y = 0;
        this.A = 0;
        this.I = 0;
        this.PC = 0;
        C = false;
        N = false;
        Z = false;

        instructions = new Runnable[0x100];
        setUpInstructions();

        this.isRunning = false;
        this.rand = new Random();

        loadProgram(program);
    }

    public synchronized void loadProgram(Program program) {
        byte[] rawProgram = program.getProgram();
        for (int i = PC; i < (PC + rawProgram.length); i++) {
            memory.write((short)i, rawProgram[i - PC]);
        }
        this.PC = memory.fetchWord((short) 0);
    }
    @Override
    public void run() {
        this.loop();
    }

    /**
     * @param instructions the max number of instructions to execute
     * @return the number of instructions executed before exit
     */
    public long run(long instructions) {
        remainInstr = instructions;
        this.run();
        return  cycleCount;
    }

    public byte[] dumpMemory() {
        return memory.copyData();
    }

    public void logMemory() {
        byte[] mem = memory.copyData();

        // find end
        int end = 0;
        for (int i = 0; i < mem.length; i++) {
            if(mem[i] != 0)
                end = i;
        }

        end += 15 - (end % 16);

        // print
        System.out.print("\n");
        System.out.println("Memory:");
        for (int i = 0; i <= end; i++) {
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
        System.out.print("\n\n");
    }

    private void setUpInstructions() {

        /*
                Load and Store
         */
        instructions[0x10] = () ->          // $10 - LDA 0x0000    Load A from specified constant, set N and Z
                setA(fetchPCW());
        instructions[0x11] = () ->          // $11 - LDA I         Load A from mem(I), set N and Z
                setA(memory.fetchWord(I));
        instructions[0x12] = () ->          // $12 - LDI 0x0000    Load I from specified constant
                I = fetchPCW();
        instructions[0x13] = () ->          // $13 - LDX 0x0000    Load X from specified constant
                X = fetchPCW();
        instructions[0x14] = () ->          // $14 - LDX I         Load X from mem(I)
                X = memory.fetchWord(I);
        instructions[0x15] = () ->          // $15 - LDY 0x0000    Load Y from specified constant
                Y = fetchPCW();
        instructions[0x16] = () ->          // $16 - LDY I         Load Y from mem(I)
                Y = memory.fetchWord(I);
        instructions[0x17] = () ->          // $17 - STA I         Store A to mem(I)
                memory.writeW(I, A);
        instructions[0x18] = () ->          // $18 - STX I         Store X to mem(I)
                memory.writeW(I, X);
        instructions[0x19] = () ->          // $19 - STY I         Store Y to mem(I)
                memory.writeW(I, Y);

        /*
                Transfer Registers
         */
        instructions[0x20] = () ->          // $20 - TAX           Transfer A to X
                X = A;
        instructions[0x21] = () ->          // $21 - TAY           Transfer A to Y
                Y = A;
        instructions[0x22] = () ->          // $22 - TAI           Transfer A to I
                I = A;
        instructions[0x23] = () ->          // $23 - TIA           Transfer I to A, set N and Z
                setA(I);
        instructions[0x24] = () ->          // $24 - TIX           Transfer I to X
                X = I;
        instructions[0x25] = () ->          // $25 - TIY           Transfer I to Y
                Y = A;
        instructions[0x26] = () ->          // $26 - TXA           Transfer X to A, set N and Z
                setA(X);
        instructions[0x27] = () ->          // $27 - TXI           Transfer X to I
                I = X;
        instructions[0x28] = () ->          // $28 - TXY           Transfer X to Y
                Y = X;
        instructions[0x29] = () ->          // $29 - TYA           Transfer Y to A, set N and Z
                setA(Y);
        instructions[0x2A] = () ->          // $2A - TYI           Transfer Y to I
                I = Y;
        instructions[0x2B] = () ->          // $2B - TYX           Transfer Y to X
                X = Y;

        /*
                Jumps And Routine
         */
        instructions[0x0A] = () ->          // $0A - CAL 0x0000    Push PC on stack and set PC to specified constant
                pushPC(fetchPCW());
        instructions[0x0B] = () ->          // $0B - CAL I         Push PC on stack and set PC to I
                pushPC(I);
        instructions[0x0C] = () ->          // $0C - RET           Pop address from stack and set it to PC
                PC = stack.pop();
        instructions[0x0D] = () ->          // $0D - JMP 0x0000    Set PC to specified constant
                PC = fetchPCW();
        instructions[0x0E] = () ->          // $0E - JMP I         Set PC to I
                PC = I;

        /*
                System
         */
        instructions[0x00] = () ->          // $00 - EXIT          Stop the program
                isRunning = false;
        instructions[0x01] = () ->          // $01 - IN            Interrupt program until Input from device
                System.out.println("Not implemented: $01 - IN");    // Todo

        /*
                Logical
         */
        instructions[0x30] = () ->          // $30 - AND 0x0000    perform logical AND on A with specified constant and save to A, set N and Z
                setA((short) (A & fetchPCW()));
        instructions[0x31] = () ->          // $31 - AND I         perform logical AND on A with mem(I) and save to A, set N and Z
                setA((short) (A & memory.fetchWord(I)));
        instructions[0x32] = () ->          // $32 - AND X         perform logical AND on A with X and save to A, set N and Z
                setA((short) (A & X));
        instructions[0x33] = () ->          // $33 - AND Y         perform logical AND on A with Y and save to A, set N and Z
                setA((short) (A & Y));
        instructions[0x34] = () ->          // $34 - OR 0x0000     perform logical OR on A with specified constant and save to A, set N and Z
                setA((short) (A | fetchPCW()));
        instructions[0x35] = () ->          // $35 - OR I          perform logical OR on A with mem(I) and save to A, set N and Z
                setA((short) (A | memory.fetchWord(I)));
        instructions[0x36] = () ->          // $36 - OR X          perform logical OR on A with X and save to A, set N and Z
                setA((short) (A | X));
        instructions[0x37] = () ->          // $37 - OR Y          perform logical OR on A with Y and save to A, set N and Z
                setA((short) (A | Y));
        instructions[0x38] = () ->          // $38 - XOR 0x0000    perform logical EXCLUSIVE OR on A with specified constant and save to A, set N and Z
                setA((short) (A ^ fetchPCW()));
        instructions[0x39] = () ->          // $39 - XOR I         perform logical EXCLUSIVE OR on A with mem(I) and save to A, set N and Z
                setA((short) (A ^ memory.fetchWord(I)));
        instructions[0x3A] = () ->          // $3A - XOR X         perform logical EXCLUSIVE OR on A with X and save to A, set N and Z
                setA((short) (A ^ X));
        instructions[0x3B] = () ->          // $3B - XOR Y         perform logical EXCLUSIVE OR on A with Y and save to A, set N and Z
                setA((short) (A ^ Y));

        /*
                Arithmetic
         */
        instructions[0x40] = () ->          // $40 - ADD 0x0000    Add specified constant to A, set N, Z and C
                addA(fetchPCW());
        instructions[0x41] = () ->          // $41 - ADD I         Add mem(I) constant to A, set N, Z and C
                addA(memory.fetchWord(I));
        instructions[0x42] = () ->          // $42 - ADD X         Add X constant to A, set N, Z and C
                addA(X);
        instructions[0x43] = () ->          // $43 - ADD Y         Add Y constant to A, set N, Z and C
                addA(Y);
        instructions[0x44] = () ->          // $44 - SUB 0x0000    Subtract specified constant from A, set N, Z and C
                subA(fetchPCW());
        instructions[0x45] = () ->          // $45 - SUB I         Subtract mem(I) constant from A, set N, Z and C
                subA(memory.fetchWord(I));
        instructions[0x46] = () ->          // $46 - SUB X         Subtract X constant from A, set N, Z and C
                subA(X);
        instructions[0x47] = () ->          // $47 - SUB Y         Subtract Y constant from A, set N, Z and C
                subA(Y);
        instructions[0x48] = () ->          // $48 - CMP 0x0000    Subtract specified constant from A and just set the Flags, set N, Z and C
                compareA(fetchPCW());
        instructions[0x49] = () ->          // $49 - CMP I         Subtract mem(I) from A and just set the Flags, set N, Z and C
                compareA(memory.fetchWord(I));
        instructions[0x4A] = () ->          // $4A - CMP X         Subtract X from A and just set the Flags, set N, Z and C
                compareA(X);
        instructions[0x4B] = () ->          // $4B - CMP Y         Subtract Y from A and just set the Flags, set N, Z and C
                compareA(Y);

        /*
                Shifts
         */
        instructions[0x4C] = () -> {        // $4C - SL            Shift bits of A left by one, set N, Z and C
            C = (A & 0b1000000000000000) != 0;
            A <<= 1;
            setNZFlags();
        };
        instructions[0x4D] = () -> {        // $4D - SR            Shift bits of A right by one, set N, Z and C
            C = (A & 0b0000000000000001) != 0;
            A >>>= 1;
            setNZFlags();
        };
        instructions[0x4E] = () -> {        // $4E - ROL           Rotate bits of A left by one, set N, Z and C
            C = (A & 0b1000000000000000) != 0;
            A = (short) ((A << 1) | ((A) >>> (15)));
            setNZFlags();
        };
        instructions[0x4F] = () -> {        // $4F - ROR           Rotate bits of A right by one, set N, Z and C
            C = (A & 0b0000000000000001) != 0;
            A = (short) ((A >>> 1) | ((A) << (15)));
            setNZFlags();
        };

        /*
                Branches
         */
        instructions[0x50] = () ->          // $50 - BCS I         Jump to I, if carry flag is set
            { if (C) PC = I; };
        instructions[0x51] = () ->          // $51 - BCS 0x0000    Increase PC by specified constant if carry flag is set
            { if (C) PC += fetchPCW(); };
        instructions[0x52] = () ->          // $52 - BCC I         Jump to I, if carry flag is clear
            { if (!C) PC = I; };
        instructions[0x53] = () ->          // $53 - BCC 0x0000    Increase PC by specified constant if carry flag is clear
            { if (!C) PC += fetchPCW(); };
        instructions[0x54] = () ->          // $54 - BNS I         Jump to I, if negative flag is set
            { if (N) PC = I; };
        instructions[0x55] = () ->          // $55 - BNS 0x0000    Increase PC by specified constant if negative flag is set
            { if (N) PC += fetchPCW(); };
        instructions[0x56] = () ->          // $56 - BNC I         Jump to I, if negative flag is clear
            { if (!N) PC = I; };
        instructions[0x57] = () ->          // $57 - BNC 0x0000    Increase PC by specified constant if negative flag is clear
            { if (!N) PC += fetchPCW(); };
        instructions[0x58] = () ->          // $58 - BZS I          Jump to I, if zero flag is set
            { if (Z) PC = I; };
        instructions[0x59] = () ->          // $59 - BZS 0x0000     Increase PC by specified constant if zero flag is set
            { if (Z) PC += fetchPCW(); };
        instructions[0x5A] = () ->          // $5A - BZC I          Jump to I, if zero flag is clear
            { if (!Z) PC = I; };
        instructions[0x5B] = () ->          // $5B - BZC 0x0000     Increase PC by specified constant if zero flag is clear
            { if (!Z) PC += fetchPCW(); };
    }

    private void loop() {
        System.out.println("START LOOP =======================");

        isRunning = true;

        /* debug info */
        cycleCount = 0;
        long startTime = System.currentTimeMillis();
        /* debug info */

        while(isRunning && remainInstr > 0) {
            cycle();
            cycleCount++;

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

        Runnable method = instructions[opcode];
        if(method != null)
            method.run();
        else
            System.out.println("Opcode " + Integer.toHexString(opcode) + " not found!");
    }

    private byte fetchPC() {
        byte data = memory.fetch(PC);
        PC++;
        return data;
    }

    private short fetchPCW() {
        short data = (short)(memory.fetch(PC) << 8);
        PC++;
        data |= 0x00FF & memory.fetch(PC);
        PC++;
        return data;
    }

    private void setA(short a) {
        this.A = a;
        setNZFlags();
    }

    private void setNZFlags() {
        N = A < 0;
        Z = A == 0;
    }

    private void addA(short add) {
        C = A + add > Short.MAX_VALUE;
        A = (short) (A + add);
        setNZFlags();
    }

    private void subA(short sub) {
        C = A - sub < Short.MIN_VALUE;
        A = (short) (A - sub);
        setNZFlags();
    }

    private void compareA(short com) {
        C = A - com < Short.MIN_VALUE;
        com = (short) (A - com);
        N = com < 0;
        Z = com == 0;
    }

    private void pushPC(short newAddress) {
        stack.push(PC);
        PC = newAddress;
    }

}
