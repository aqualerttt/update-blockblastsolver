package com.blockblast.solver.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.blockblast.solver.detector.BoardDetector;
import com.blockblast.solver.solver.BlockSolver;

public class OverlayView extends View {

    private static final int[] PIECE_COLORS = {
            0xAA00E5FF,   // cyan
            0xAAFFD600,   // yellow
            0xAAFF4081,   // pink
    };

    private static final int STROKE_COLOR  = 0xFFFFFFFF;

    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float boardLeft, boardTop, boardRight, boardBottom;
    private float trayTop, trayBottom;
    private float cellW, cellH;

    private BlockSolver.Placement[] placements;
    
    // Geometry feedback arrays mapped directly from Detector thread calculations
    private final int[] piecesMinX = new int[3];
    private final int[] piecesMaxX = new int[3];
    private final int[] piecesMinY = new int[3];
    private final int[] piecesMaxY = new int[3];
    private final boolean[] piecesActive = new boolean[3];
    
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    public OverlayView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f);
        strokePaint.setColor(STROKE_COLOR);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(0x44FFFFFF);

        debugPaint.setStyle(Paint.Style.STROKE);
        debugPaint.setStrokeWidth(4f);
    }

    public void update(BoardDetector detector, BlockSolver.Placement[] placements, int screenW, int screenH) {
        this.placements = placements;

        // Calculate scaling offsets if canvas display properties slightly mismatch raw image targets
        if (detector.debugW > 0 && detector.debugH > 0) {
            this.scaleX = (float) screenW / detector.debugW;
            this.scaleY = (float) screenH / detector.debugH;
        }

        this.boardLeft   = BoardDetector.BOARD_LEFT_PCT   * screenW;
        this.boardTop    = BoardDetector.BOARD_TOP_PCT    * screenH;
        this.boardRight  = BoardDetector.BOARD_RIGHT_PCT  * screenW;
        this.boardBottom = BoardDetector.BOARD_BOTTOM_PCT * screenH;
        
        this.trayTop     = BoardDetector.TRAY_TOP_PCT     * screenH;
        this.trayBottom  = BoardDetector.TRAY_BOTTOM_PCT  * screenH;

        this.cellW = (boardRight - boardLeft) / BoardDetector.GRID;
        this.cellH = (boardBottom - boardTop) / BoardDetector.GRID;

        // Extract raw tracking dimensions from scanning state
        for (int p = 0; p < 3; p++) {
            this.piecesActive[p] = detector.slotHasPiece[p];
            this.piecesMinX[p]   = detector.detectedMinX[p];
            this.piecesMaxX[p]   = detector.detectedMaxX[p];
            this.piecesMinY[p]   = detector.detectedMinY[p];
            this.piecesMaxY[p]   = detector.detectedMaxY[p];
        }

        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float screenW = canvas.getWidth();
        
        // 1. Draw Adaptive Search Columns (Yellow Window Targets)
        float[][] slotBoundsX = {
            {0.03f * screenW, 0.36f * screenW},
            {0.36f * screenW, 0.64f * screenW},
            {0.64f * screenW, 0.97f * screenW}
        };

        debugPaint.setStyle(Paint.Style.STROKE);
        for (int p = 0; p < 3; p++) {
            debugPaint.setColor(Color.YELLOW);
            debugPaint.setStrokeWidth(2f);
            canvas.drawRect(slotBoundsX[p][0], trayTop, slotBoundsX[p][1], trayBottom, debugPaint);

            // 2. Draw Green Alignment Rects over the tracked bounding box bounds
            if (piecesActive[p]) {
                debugPaint.setColor(Color.GREEN);
                debugPaint.setStrokeWidth(4f);
                canvas.drawRect(
                        piecesMinX[p] * scaleX, 
                        piecesMinY[p] * scaleY, 
                        piecesMaxX[p] * scaleX, 
                        piecesMaxY[p] * scaleY, 
                        debugPaint
                );
            }
        }

        // 3. Draw Game Grid Matrix Base Lines
        for (int r = 0; r <= BoardDetector.GRID; r++)
            canvas.drawLine(boardLeft, boardTop + r * cellH, boardRight, boardTop + r * cellH, gridPaint);
        for (int c = 0; c <= BoardDetector.GRID; c++)
            canvas.drawLine(boardLeft + c * cellW, boardTop, boardLeft + c * cellW, boardBottom, gridPaint);

        if (placements == null) return;

        // 4. Render Solved Optimal Move Output Recommendations
        for (int p = 0; p < placements.length; p++) {
            BlockSolver.Placement pl = placements[p];
            if (pl == null) continue;

            fillPaint.setColor(PIECE_COLORS[p]);
            strokePaint.setColor(PIECE_COLORS[p] | 0xFF000000);

            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (!pl.shape[r][c]) continue;
                    int br = pl.row + r;
                    int bc = pl.col + c;
                    if (br >= BoardDetector.GRID || bc >= BoardDetector.GRID) continue;

                    RectF cell = new RectF(
                            boardLeft + bc * cellW + 4,
                            boardTop  + br * cellH + 4,
                            boardLeft + bc * cellW + cellW - 4,
                            boardTop  + br * cellH + cellH - 4
                    );
                    canvas.drawRoundRect(cell, 8, 8, fillPaint);
                    canvas.drawRoundRect(cell, 8, 8, strokePaint);
                }
            }

            fillPaint.setColor(Color.WHITE);
            fillPaint.setTextSize(36f);
            fillPaint.setStyle(Paint.Style.FILL);
            canvas.drawText("P" + (p + 1),
                    boardLeft + pl.col * cellW + 8,
                    boardTop  + pl.row * cellH + 42,
                    fillPaint);
        }
    }
}
