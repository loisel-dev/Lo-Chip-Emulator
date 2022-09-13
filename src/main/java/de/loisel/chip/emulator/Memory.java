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

public class Memory {
    public static final int MEMORY_SIZE = 0x10000;

    private byte[] data;

    public Memory() {
        this.data = new byte[MEMORY_SIZE];
    }

    public byte fetch(short address) {
        int addr = Short.toUnsignedInt(address);
        return data[addr];
    }

    public byte[] fetchArray(short address, int length) {
        byte[] arr = new byte[length];
        for (int i = 0; i < length && i + address < MEMORY_SIZE; i++) {
            arr[i] = data[address + i];
        }
        return arr;
    }

    public void write(short address, byte data) {
        int addr = Short.toUnsignedInt(address);
        this.data[addr] = data;
    }

    public void writeW(short address, short word) {
        int addr = Short.toUnsignedInt(address);
        this.data[addr] = (byte) (word >>> 8);
        this.data[addr + 1] = (byte) (word & 0x00FF);
    }

    public void reset() {
        this.data = new byte[MEMORY_SIZE];
    }

    public short fetchWord(short address) {
        if(address < data.length - 1)
            return (short) ((data[address] << 8) | (data[address + 1] & 0xFF));
        else
            return 0;
    }

    public byte[] copyData() {
        byte[] newData = new byte[MEMORY_SIZE];
        System.arraycopy(data,0,newData,0,MEMORY_SIZE);
        return newData;
    }
}
