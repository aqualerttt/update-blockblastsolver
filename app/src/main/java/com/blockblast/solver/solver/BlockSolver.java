package com.blockblast.solver.solver;

import com.blockblast.solver.detector.BoardDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential solver for Block Blast.
 *
 * Tries every ordering of the active pieces and every valid placement for
 * each, tracking the board as it evolves (so combo clears across pieces are
 * accounted for). Among sequences that place the most pieces, picks the one
 * that clears the most lines in total; ties are broken by a "density" score
 * that rewards keeping filled cells compact (fewer scattered single-cell
 * holes).
 *
 * This mirrors the search used in the original notebook / HTML solver tool,
 * adapted to this project's board convention: board[r][c] == true means
 * "occupied", and piece shapes are normalised to a flush top-left 5x5
 * bounding box with (row, col) as the top-left placement corner.
 */
public class BlockSolver {

    public static final int GRID = BoardDetector.GRID;

    public static class Placement {
        public int piece;   // 0-2
        public int row;     // top-left row on board
        public int col;     // top-left col on board
        public double score; // total lines cleared by the chosen sequence
        public boolean[][] shape; // 5x5 canonical (flush top-left) shape

        public Placement(int piece, int row, int col, double score, boolean[][] shape) {
            this.piece = piece; this.row = row; this.col = col;
            this.score = score; this.shape = shape;
        }
    }

    private static class Best {
        List<int[]> moves; // each = {pieceIndex, row, col}
        int clears = -1;
        double density = -1;
    }

    /**
     * @param board   8x8 current board state (true = occupied)
     * @param pieces  3 x 5x5 piece shapes
     * @return best Placement for each piece slot (null if that piece isn't
     *         part of the best sequence, e.g. when the board is too full to
     *         place all 3)
     */
    public static Placement[] solve(boolean[][] board, boolean[][][] pieces) {
        boolean[][][] shapes = new boolean[3][][];
        List<Integer> active = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            shapes[p] = normalise(pieces[p]);
            if (!isEmpty(shapes[p])) active.add(p);
        }

        Placement[] result = new Placement[3];
        if (active.isEmpty()) return result;

        // Try placing all active pieces, then fewer, until something fits.
        for (int k = active.size(); k >= 1; k--) {
            Best best = new Best();
            for (List<Integer> combo : combinations(active, k)) {
                for (List<Integer> seq : permutations(combo)) {
                    search(seq, 0, board, new ArrayList<>(), 0, shapes, best);
                }
            }
            if (best.moves != null) {
                for (int[] mv : best.moves) {
                    int p = mv[0];
                    result[p] = new Placement(p, mv[1], mv[2], best.clears, shapes[p]);
                }
                return result;
            }
        }
        return result;
    }

    /** Recursively tries placing seq.get(idx)... on `grid`, updating `best`. */
    private static void search(List<Integer> seq, int idx, boolean[][] grid,
                                List<int[]> moves, int clears,
                                boolean[][][] shapes, Best best) {
        if (idx == seq.size()) {
            double d = density(grid);
            if (best.moves == null || clears > best.clears
                    || (clears == best.clears && d > best.density)) {
                best.moves = new ArrayList<>(moves);
                best.clears = clears;
                best.density = d;
            }
            return;
        }

        int p = seq.get(idx);
        boolean[][] shape = shapes[p];

        for (int r = 0; r < GRID; r++) {
            for (int c = 0; c < GRID; c++) {
                if (!canPlace(grid, shape, r, c)) continue;

                boolean[][] placed = place(grid, shape, r, c);
                int newClears = countFullLines(placed);
                boolean[][] next = clearLines(placed);

                moves.add(new int[]{p, r, c});
                search(seq, idx + 1, next, moves, clears + newClears, shapes, best);
                moves.remove(moves.size() - 1);
            }
        }
    }

    // ── Permutations / combinations over small Integer lists ───────────────

    private static List<List<Integer>> permutations(List<Integer> arr) {
        List<List<Integer>> res = new ArrayList<>();
        if (arr.size() <= 1) {
            res.add(new ArrayList<>(arr));
            return res;
        }
        for (int i = 0; i < arr.size(); i++) {
            List<Integer> rest = new ArrayList<>(arr);
            Integer cur = rest.remove(i);
            for (List<Integer> p : permutations(rest)) {
                List<Integer> combo = new ArrayList<>();
                combo.add(cur);
                combo.addAll(p);
                res.add(combo);
            }
        }
        return res;
    }

    private static List<List<Integer>> combinations(List<Integer> arr, int k) {
        List<List<Integer>> res = new ArrayList<>();
        if (k == 0) {
            res.add(new ArrayList<>());
            return res;
        }
        if (arr.size() < k) return res;

        Integer first = arr.get(0);
        List<Integer> rest = arr.subList(1, arr.size());

        for (List<Integer> c : combinations(rest, k - 1)) {
            List<Integer> withFirst = new ArrayList<>();
            withFirst.add(first);
            withFirst.addAll(c);
            res.add(withFirst);
        }
        res.addAll(combinations(rest, k));
        return res;
    }

    // ── Board / shape helpers ────────────────────────────────────────────

    /** Strip empty border rows/cols so shape is flush top-left. */
    private static boolean[][] normalise(boolean[][] raw) {
        int minR = 5, maxR = -1, minC = 5, maxC = -1;
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                if (raw[r][c]) {
                    if (r < minR) minR = r; if (r > maxR) maxR = r;
                    if (c < minC) minC = c; if (c > maxC) maxC = c;
                }
        if (maxR < 0) return new boolean[5][5]; // empty
        boolean[][] out = new boolean[5][5];
        for (int r = minR; r <= maxR; r++)
            for (int c = minC; c <= maxC; c++)
                out[r - minR][c - minC] = raw[r][c];
        return out;
    }

    private static boolean isEmpty(boolean[][] s) {
        for (boolean[] row : s) for (boolean b : row) if (b) return false;
        return true;
    }

    private static boolean canPlace(boolean[][] board, boolean[][] shape, int tr, int tc) {
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++) {
                if (!shape[r][c]) continue;
                int br = tr + r, bc = tc + c;
                if (br >= GRID || bc >= GRID) return false;
                if (board[br][bc]) return false;
            }
        return true;
    }

    private static boolean[][] place(boolean[][] board, boolean[][] shape, int tr, int tc) {
        boolean[][] next = copy(board);
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                if (shape[r][c]) next[tr + r][tc + c] = true;
        return next;
    }

    /** Returns a copy of b with any fully-occupied rows/cols cleared. */
    private static boolean[][] clearLines(boolean[][] b) {
        boolean[][] next = copy(b);
        for (int r = 0; r < GRID; r++) {
            boolean full = true;
            for (int c = 0; c < GRID; c++) if (!next[r][c]) { full = false; break; }
            if (full) for (int c = 0; c < GRID; c++) next[r][c] = false;
        }
        for (int c = 0; c < GRID; c++) {
            boolean full = true;
            for (int r = 0; r < GRID; r++) if (!next[r][c]) { full = false; break; }
            if (full) for (int r = 0; r < GRID; r++) next[r][c] = false;
        }
        return next;
    }

    /** Counts how many rows + columns in b are currently fully occupied. */
    private static int countFullLines(boolean[][] b) {
        int n = 0;
        for (int r = 0; r < GRID; r++) {
            boolean full = true;
            for (int c = 0; c < GRID; c++) if (!b[r][c]) { full = false; break; }
            if (full) n++;
        }
        for (int c = 0; c < GRID; c++) {
            boolean full = true;
            for (int r = 0; r < GRID; r++) if (!b[r][c]) { full = false; break; }
            if (full) n++;
        }
        return n;
    }

    /**
     * Higher = filled cells are more clustered together (fewer scattered
     * single-cell holes). For each occupied cell, scores the fraction of its
     * in-bounds neighbours that are also occupied, summed over the board.
     */
    private static double density(boolean[][] b) {
        double score = 0;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                if (!b[i][j]) continue;
                int filledN = 0, total = 0;
                if (i > 0)        { total++; if (b[i - 1][j]) filledN++; }
                if (i < GRID - 1) { total++; if (b[i + 1][j]) filledN++; }
                if (j > 0)        { total++; if (b[i][j - 1]) filledN++; }
                if (j < GRID - 1) { total++; if (b[i][j + 1]) filledN++; }
                if (total > 0) score += (double) filledN / total;
            }
        }
        return score;
    }

    private static boolean[][] copy(boolean[][] src) {
        boolean[][] out = new boolean[GRID][GRID];
        for (int r = 0; r < GRID; r++) out[r] = src[r].clone();
        return out;
    }
}
