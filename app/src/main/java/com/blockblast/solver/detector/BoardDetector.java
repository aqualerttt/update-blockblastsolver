package com.blockblast.solver.detector;

import android.content.Context;
import android.graphics.Bitmap;

public class BoardDetector {

    public static final int GRID = 8;
    public static final int PIECES = 3;

    // Stable main board percentage metrics
    public static final float BOARD_TOP_PCT    = 0.242f;
    public static final float BOARD_LEFT_PCT   = 0.055f;
    public static final float BOARD_RIGHT_PCT  = 0.944f;
    public static final float BOARD_BOTTOM_PCT = 0.665f;
    
    // Hard baseline definition for the item tray zone
    public static final float TRAY_TOP_PCT     = 0.730f; 
    public static final float TRAY_BOTTOM_PCT  = 0.895f; 

    public boolean[][] board = new boolean[GRID][GRID];
    public boolean[][][] pieces = new boolean[PIECES][5][5];

    // Shared runtime geometry passed dynamically to visual OverlayView
    public int debugW = 0;
    public int debugH = 0;
    public int debugCellW = 0;
    public int debugCellH = 0;
    public int[] detectedMinX = new int[PIECES];
    public int[] detectedMaxX = new int[PIECES];
    public int[] detectedMinY = new int[PIECES];
    public int[] detectedMaxY = new int[PIECES];
    public boolean[] slotHasPiece = new boolean[PIECES];

    public void detect(Bitmap bmp, final Context context) {
        int W = bmp.getWidth();
        int H = bmp.getHeight();
        
        this.debugW = W;
        this.debugH = H;

        int left = (int)(BOARD_LEFT_PCT * W);
        int right = (int)(BOARD_RIGHT_PCT * W);
        int top = (int)(BOARD_TOP_PCT * H);
        int bottom = (int)(BOARD_BOTTOM_PCT * H);
        
        int cellW = (right - left) / GRID;
        int cellH = (bottom - top) / GRID;
        
        this.debugCellW = cellW;
        this.debugCellH = cellH;

        // 1. Scan Main Board Matrix
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int px = left + col * cellW + cellW / 2;
                int py = top + row * cellH + cellH / 2;
                if (px < W && py < H) {
                    int color = bmp.getPixel(px, py);
                    int r = (color >> 16) & 0xFF; 
                    int g = (color >> 8) & 0xFF; 
                    int b = color & 0xFF;
                    // Luma threshold to separate empty slots from background grid accents
                    board[row][col] = (0.299 * r + 0.587 * g + 0.114 * b) > 65;
                }
            }
        }

        // 2. Color-Agnostic Scan via Dynamic Bounding Box Localization
        int tTop = (int)(TRAY_TOP_PCT * H);
        int tBot = (int)(TRAY_BOTTOM_PCT * H);

        // Define explicit searching windows for Slots 1, 2, and 3
        float[][] slotBoundsX = {
            {0.03f * W, 0.36f * W}, // Slot 1 search window bounds
            {0.36f * W, 0.64f * W}, // Slot 2 search window bounds
            {0.64f * W, 0.97f * W}  // Slot 3 search window bounds
        };

        // Reset pieces matrix maps clean
        for (int p = 0; p < PIECES; p++) {
            slotHasPiece[p] = false;
            detectedMinX[p] = Integer.MAX_VALUE;
            detectedMaxX[p] = Integer.MIN_VALUE;
            detectedMinY[p] = Integer.MAX_VALUE;
            detectedMaxY[p] = Integer.MIN_VALUE;
            
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    pieces[p][r][c] = false;
                }
            }
        }

        for (int p = 0; p < PIECES; p++) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

            // Step A: Find the shape bounding limits inside the tray window based on Luma contrast
            for (int py = tTop; py < tBot; py += 4) { 
                for (int px = (int)slotBoundsX[p][0]; px < (int)slotBoundsX[p][1]; px += 4) {
                    if (px >= W || py >= H) continue;
                    
                    int color = bmp.getPixel(px, py);
                    int r = (color >> 16) & 0xFF; 
                    int g = (color >> 8) & 0xFF; 
                    int b = color & 0xFF;
                    int luma = (int)(0.299 * r + 0.587 * g + 0.114 * b);

                    // A Luma above 70 means a structural piece asset color (agnostic of hue variations)
                    if (luma > 70) { 
                        if (px < minX) minX = px; if (px > maxX) maxX = px;
                        if (py < minY) minY = py; if (py > maxY) maxY = py;
                    }
                }
            }

            // Guard: If boundaries didn't adjust, tray slot is verified empty
            if (maxX <= minX || maxY <= minY) {
                continue;
            }

            // Save visual tracking boundaries for the debugging canvas to draw
            this.detectedMinX[p] = minX;
            this.detectedMaxX[p] = maxX;
            this.detectedMinY[p] = minY;
            this.detectedMaxY[p] = maxY;
            this.slotHasPiece[p] = true;

            int pieceW = maxX - minX;
            int pieceH = maxY - minY;

            // Step B: Infer array grid constraints by comparing actual bounding box to unit cells
            int cols = Math.round((float) pieceW / cellW);
            int rows = Math.round((float) pieceH / cellH);
            cols = Math.max(1, Math.min(5, cols));
            rows = Math.max(1, Math.min(5, rows));

            // Step C: Sample from precise relative calculated center vectors inside the bounding box
            for (int rIdx = 0; rIdx < rows; rIdx++) {
                for (int cIdx = 0; cIdx < cols; cIdx++) {
                    int sampleX = minX + (int) ((cIdx + 0.5f) * ((float) pieceW / cols));
                    int sampleY = minY + (int) ((rIdx + 0.5f) * ((float) pieceH / rows));

                    if (sampleX < W && sampleY < H) {
                        int color = bmp.getPixel(sampleX, sampleY);
                        int r = (color >> 16) & 0xFF; 
                        int g = (color >> 8) & 0xFF; 
                        int b = color & 0xFF;
                        int luma = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                        
                        // Populate clean top-left oriented structures for the canonical solver matrix
                        pieces[p][rIdx][cIdx] = (luma > 70);
                    }
                }
            }
        }
    }
}
