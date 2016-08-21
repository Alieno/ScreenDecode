/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.Android;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.LuminanceSource;

/**
 * This object extends LuminanceSource around an array of YUV data returned from the camera driver,
 * with the option to crop to a rectangle within the full data. This can be used to exclude
 * superfluous pixels around the perimeter and speed up decoding.
 * <p/>
 * It works for any pixel format where the Y channel is planar and appears first, including
 * YCbCr_420_SP and YCbCr_422_SP.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class PlanarYUVLuminanceSource extends LuminanceSource {
    private final byte[] yuvData;
    private final int dataWidth;
    private final int dataHeight;
    private final int left;
    private final int top;

    public PlanarYUVLuminanceSource(byte[] yuvData, int dataWidth, int dataHeight, int left, int top,
                                    int width, int height) {
        super(width, height);

        if (left + width > dataWidth || top + height > dataHeight) {
            throw new IllegalArgumentException("Crop rectangle does not fit within image data.");
        }

        this.yuvData = yuvData;
        this.dataWidth = dataWidth;
        this.dataHeight = dataHeight;
        this.left = left;
        this.top = top;
    }

    @Override
    public byte[] getRow(int y, byte[] row) {
        if (y < 0 || y >= getHeight()) {
            throw new IllegalArgumentException("Requested row is outside the image: " + y);
        }
        int width = getWidth();
        if (row == null || row.length < width) {
            row = new byte[width];
        }
        int offset = (y + top) * dataWidth + left;
        System.arraycopy(yuvData, offset, row, 0, width);
        return row;
    }

    @Override
    public byte[] getMatrix() {
        int width = getWidth();
        int height = getHeight();

        // If the caller asks for the entire underlying image, save the copy and give them the
        // original data. The docs specifically warn that result.length must be ignored.
        if (width == dataWidth && height == dataHeight) {
            return yuvData;
        }

        int area = width * height;
        byte[] matrix = new byte[area];
        int inputOffset = top * dataWidth + left;

        // If the width matches the full width of the underlying data, perform a single copy.
        if (width == dataWidth) {
            System.arraycopy(yuvData, inputOffset, matrix, 0, area);
            return matrix;
        }

        // Otherwise copy one cropped row at a time.
        byte[] yuv = yuvData;
        for (int y = 0; y < height; y++) {
            int outputOffset = y * width;
            System.arraycopy(yuv, inputOffset, matrix, outputOffset, width);
            inputOffset += dataWidth;
        }
        return matrix;
    }

    @Override
    public boolean isCropSupported() {
        return true;
    }

    public int getDataWidth() {
        return dataWidth;
    }

    public int getDataHeight() {
        return dataHeight;
    }

    public Bitmap renderCroppedGreyscaleBitmap() {
        int width = getWidth();
        int height = getHeight();
        int[] pixels = new int[width * height];
        int[] tmpPixels = new int[width * height];
        byte[] yuv = yuvData;
        int inputOffset = top * dataWidth + left;

        for (int y = 0; y < height; y++) {
            int outputOffset = y * width;
            for (int x = 0; x < width; x++) {
                tmpPixels[outputOffset + x] = yuv[inputOffset + x];
            }
            inputOffset += dataWidth;
        }

//        for (int y = 0; y < height; y++) {
//            int outputOffset = y * width;
//            for (int x = 0; x < width; x++) {
//                int grey = yuv[inputOffset + x] & 0xff;
//                pixels[outputOffset + x] = 0xFF000000 | (grey * 0x00010101);
//            }
//            inputOffset += dataWidth;
//        }
        int[] laplacian = new int[] { -1, -1, -1, -1, 9, -1, -1, -1, -1 };
        int pixR = 0;
        int pixG = 0;
        int pixB = 0;

        int pixColor = 0;

        int newR = 0;
        int newG = 0;
        int newB = 0;

        int idx = 0;
        float alpha = 2.0F;
        int[] pixels2 = new int[width * height];

        for (int i = 1, length = height - 1; i < length; i++)
        {
            for (int k = 1, len = width - 1; k < len; k++)
            {
                idx = 0;
                for (int m = -1; m <= 1; m++)
                {
                    for (int n = -1; n <= 1; n++)
                    {
                        pixColor = tmpPixels[(i + n) * width + k + m];
                        pixR = Color.red(pixColor);
                        pixG = Color.green(pixColor);
                        pixB = Color.blue(pixColor);

                        newR = newR + (int) (pixR * laplacian[idx] * alpha);
                        newG = newG + (int) (pixG * laplacian[idx] * alpha);
                        newB = newB + (int) (pixB * laplacian[idx] * alpha);
                        idx++;
                    }
                }

                newR = Math.min(255, Math.max(0, newR));
                newG = Math.min(255, Math.max(0, newG));
                newB = Math.min(255, Math.max(0, newB));

                pixels2[i * width + k] = Color.argb(255, newR, newG, newB);
                newR = 0;
                newG = 0;
                newB = 0;
            }
        }


        for (int y = 0; y < height * width; y++){
            int grey = pixels2[y] & 0xff;
            pixels[y] = 0xFF000000 | (grey * 0x00010101);
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }


    //Get YUV
    public byte[] getTheYUV() {
        int width = getWidth();
        int height = getHeight();
        int y = 0;
        int x = 0;
        int nOriganlYLen = dataWidth * dataHeight;
        byte[] yuv = yuvData;
        byte[] ret = new byte[width * height * 3 / 2];

        int inputOffset = top * dataWidth + left;
        for (y = 0; y < height; y++) {
            int outputOffset = y * width;
            for (x = 0; x < width; x++) {
                ret[outputOffset + x] = yuv[inputOffset + x];
            }
            inputOffset += dataWidth;
        }
        for (y = 0; y < height; y += 2) {
            for (x = 0; x < width; x++) {
                ret[height * width + width * (y >> 1) + x] = yuv[nOriganlYLen + ((top + y) >> 1) * dataWidth + (x + left)];
                // retYuvBuffer[height*width + (width*(y>>1)+x+1)] = yuvData[nOriganlYLen+((top+y)>>1)*originalWidth+((x+left)>>1)+1];
            }
        }
        return ret;
    }
}
