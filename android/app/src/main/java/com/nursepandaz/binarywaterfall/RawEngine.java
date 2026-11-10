/* GPL-3.0-or-later. Android implementation of Binary Waterfall's raw-byte mapping.
 * Original desktop application: Ella Jameson (nimaid), GPL v3.
 */
package com.nursepandaz.binarywaterfall;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class RawEngine {
    public static final long MAX_BYTES = 256L * 1024 * 1024;
    private RawEngine() {}

    public static void copyBounded(InputStream input, File destination) throws IOException {
        try (OutputStream output = new FileOutputStream(destination)) {
            byte[] block = new byte[65536]; long total = 0; int n;
            while ((n = input.read(block)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                total += n;
                if (total > MAX_BYTES) throw new IOException("Choose a file smaller than 256 MiB.");
                output.write(block, 0, n);
            }
            if (total == 0) throw new IOException("The selected file is empty.");
        } catch (IOException error) { destination.delete(); throw error; }
    }

    public static long alignedBytes(long length, int bits, int channels) {
        if ((bits != 8 && bits != 16) || (channels != 1 && channels != 2))
            throw new IllegalArgumentException("Use 8/16-bit mono or stereo PCM.");
        int frame = bits / 8 * channels;
        return length - length % frame;
    }

    public static void wave(File input, File destination, int rate, int bits, int channels) throws IOException {
        if (rate < 8000 || rate > 48000) throw new IllegalArgumentException("Unsupported sample rate");
        long bytes = alignedBytes(input.length(), bits, channels);
        if (bytes == 0) throw new IOException("This file is too short for the selected audio format.");
        try (InputStream in = new FileInputStream(input); OutputStream out = new FileOutputStream(destination)) {
            out.write("RIFF".getBytes(StandardCharsets.US_ASCII)); le(out, bytes + 36, 4);
            out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)); le(out,16,4); le(out,1,2);
            le(out,channels,2); le(out,rate,4); le(out,rate * channels * bits / 8,4);
            le(out,channels * bits / 8,2); le(out,bits,2);
            out.write("data".getBytes(StandardCharsets.US_ASCII)); le(out,bytes,4);
            byte[] block = new byte[65536];
            while (bytes > 0) {
                int n = in.read(block, 0, (int)Math.min(bytes,block.length));
                if (n < 0) throw new EOFException("Input file changed");
                out.write(block,0,n); bytes -= n;
            }
        } catch (IOException error) { destination.delete(); throw error; }
    }
    private static void le(OutputStream out, long v, int n) throws IOException {
        for (int i=0;i<n;i++) out.write((int)(v >>> (i*8)) & 255);
    }

    // Center alignment and vertical flip match the desktop's defaults. Out-of-file bytes are black.
    public static int[] pixels(RandomAccessFile source, int width, int height, String format,
                               double fraction, boolean flip, boolean playhead) throws IOException {
        if (width < 4 || width > 256 || height < 4 || height > 256)
            throw new IllegalArgumentException("Image dimensions must be 4–256");
        if (!format.equals("bgrx") && !format.equals("rgb") && !format.equals("w"))
            throw new IllegalArgumentException("Unsupported color format");
        if (!Double.isFinite(fraction)) throw new IllegalArgumentException("Invalid position");
        int stride = width * format.length();
        long rows = (source.length() + stride - 1) / stride;
        long startRow = (long)Math.rint(rows * Math.max(0,Math.min(1,fraction))) - (long)Math.rint(height / 2.0);
        long address = startRow * stride;
        byte[] data = new byte[width * height * format.length()];
        int padding = (int)Math.min(data.length, Math.max(0,-address));
        source.seek(Math.max(0,address));
        int offset = padding;
        while (offset < data.length) { int n=source.read(data,offset,data.length-offset); if(n<0)break; offset+=n; }
        int[] colors = new int[width * height];
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            int index=(y*width+x)*format.length(), r=0,g=0,b=0;
            // Padding must stay black, including partial pixels near EOF.
            if(index>=padding && index+format.length()<=offset) {
                if(format.equals("w")) r=g=b=data[index]&255;
                else if(format.equals("rgb")) {r=data[index]&255;g=data[index+1]&255;b=data[index+2]&255;}
                else {b=data[index]&255;g=data[index+1]&255;r=data[index+2]&255;}
            }
            if(playhead && y==(int)Math.rint((height-1)/2.0)) {
                double lum=(0.299*r+0.587*g+0.114*b)/255;
                int contrast=lum>0.5?0:255;
                int gray=(int)Math.round((255-(r+g+b)/3.0+contrast)/2.0); r=g=b=gray;
            }
            colors[(flip?height-1-y:y)*width+x]=0xff000000 | r<<16 | g<<8 | b;
        }
        return colors;
    }
}
