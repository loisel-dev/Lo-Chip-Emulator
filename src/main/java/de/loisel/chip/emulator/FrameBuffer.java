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

public class FrameBuffer {
    private final int width;
    private final int height;

    private byte[][] buffer;

    public FrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        buffer = new byte[width][height];
    }

    public FrameBuffer() {
        this(64, 32);
    }

    public synchronized boolean setPixel(int x, int y) {
        if(buffer[x][y] == 0) {
            buffer[x][y] |= 0xFF;
            return false;
        } else {
            buffer[x][y] = 0;
            return true;
        }
    }

    public synchronized boolean setPixel(int x, int y, byte color) {
        boolean collision = buffer[x][y] != 0;
        buffer[x][y] = color;
        return collision;
    }

    public synchronized void clearBuffer() {
        buffer = new byte[width][height];
    }

    public synchronized boolean[][] copyBuffer() {
        boolean[][] buff = new boolean[width][height];
        for(int w = 0; w < width; w++) {
            for(int h = 0; h < height; h++) {
                buff[w][h] = buffer[w][h] != 0;
            }
        }
        return buff;
    }

    public synchronized byte[][] copyBBuffer() {
        return Arrays.stream(buffer).map(byte[]::clone).toArray(byte[][]::new);
    }

    /**
     * returns true on pixel collision
     */
    public synchronized boolean setSprite(byte[] sprite, byte xCord, byte yCord) {

        int xC = Byte.toUnsignedInt(xCord) % width;
        int yC = Byte.toUnsignedInt(yCord) % height;

        boolean collision = false;
        for (int i = 0; (i < sprite.length) && (yC + i < height); i++) {
            String byteString = String.format("%8s", Integer.toBinaryString(sprite[i] & 0xFF)).replace(' ', '0');
            for (int j = 0; (j < 8) && (xC + j < width); j++) {
                if(byteString.charAt(j) == '1') {
                    collision = buffer[xC + j][yC + i] != 0;
                    setPixel(xC + j, yC + i);
                }
            }
        }
        return collision;
    }
}
