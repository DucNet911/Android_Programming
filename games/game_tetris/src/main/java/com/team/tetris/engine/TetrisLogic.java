package com.team.tetris.engine;

import java.util.Random;
import java.util.Arrays;

public class TetrisLogic {
    public static final int ROWS = 20;
    public static final int COLS = 10;
    public int score = 0;

    private final StatefulRandom random;
    private final int[][] board;

    private int[][] currentPiece;
    private int[][] nextPiece;
    private int currentX;
    private int currentY;
    private boolean isGameOver = false;

    private final int[][] SHAPE_T = {{0, 1, 0}, {1, 1, 1}, {0, 0, 0}};
    private final int[][] SHAPE_O = {{2, 2}, {2, 2}};
    private final int[][] SHAPE_I = {{0, 0, 0, 0}, {3, 3, 3, 3}, {0, 0, 0, 0}, {0, 0, 0, 0}};
    private final int[][] SHAPE_Z = {{0, 0, 0}, {0, 4, 4}, {4, 4, 0}};
    private final int[][] SHAPE_S = {{0, 0, 0}, {5, 5, 0}, {0, 5, 5}};
    private final int[][] SHAPE_J = {{0, 6, 0}, {0, 6, 0}, {6, 6, 0}};
    private final int[][] SHAPE_L = {{0, 7, 0}, {0, 7, 0}, {0, 7, 7}};

    private final int[][][] ALL_SHAPES = {
            SHAPE_T, SHAPE_O, SHAPE_I, SHAPE_Z, SHAPE_S, SHAPE_J, SHAPE_L
    };

    public interface OnLineClearListener {
        void onLineCleared(int row, int col, int blockType);
    }

    private OnLineClearListener lineClearListener;

    public void setOnLineClearListener(OnLineClearListener listener) {
        this.lineClearListener = listener;
    }

    private java.util.List<Integer> bag;
    private int currentBagIndex;

    public TetrisLogic() {
        this(System.nanoTime(), true);
    }

    private TetrisLogic(long seed, boolean initialize) {
        board = new int[ROWS][COLS];
        random = new StatefulRandom(seed);

        // Khởi tạo túi và trộn ngẫu nhiên lần đầu tiên
        bag = new java.util.ArrayList<>();
        if (!initialize) return;
        refillAndShuffleBag();

        currentPiece = generateRandomPiece();
        nextPiece = generateRandomPiece();

        spawnPiece();
    }

    // Hàm mới: Nạp đầy 7 khối vào túi và xóc đều lên
    private void refillAndShuffleBag() {
        bag.clear();
        for (int i = 0; i < ALL_SHAPES.length; i++) {
            bag.add(i); // Bỏ 7 khối có ID từ 0 đến 6 vào túi
        }
        java.util.Collections.shuffle(bag, random); // Xóc túi mù
        currentBagIndex = 0; // Reset lại chỉ số bốc từ đầu
    }

    private int[][] generateRandomPiece() {
        // Nếu bốc hết 7 khối trong túi rồi thì nạp đầy và xóc túi mới
        if (currentBagIndex >= bag.size()) {
            refillAndShuffleBag();
        }

        // Bốc khối gạch theo chỉ số index hiện tại trong túi
        int pieceIndex = bag.get(currentBagIndex);
        currentBagIndex++; // Tăng chỉ số để lần sau bốc khối kế tiếp

        int[][] originalShape = ALL_SHAPES[pieceIndex];
        int n = originalShape.length;

        int[][] newPiece = new int[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(originalShape[i], 0, newPiece[i], 0, n);
        }

        int rotationTimes = random.nextInt(4);
        for (int r = 0; r < rotationTimes; r++) {
            int[][] rotatedPiece = new int[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    rotatedPiece[j][n - 1 - i] = newPiece[i][j];
                }
            }
            newPiece = rotatedPiece;
        }
        return newPiece;
    }

    public void spawnPiece() {
        currentPiece = nextPiece;
        nextPiece = generateRandomPiece();

        int n = currentPiece.length;
        // Bắt đầu từ phía trên đỉnh (nhô lên 1 đoạn so với hàng 0)
        currentY = -n + 1;
        currentX = (COLS - n) / 2;

        // Trượt dần xuống cho đến khi một phần gạch lọt vào bàn chơi
        while (currentY < 0 && isValidMove(currentPiece, currentX, currentY + 1)) {
            currentY++;
        }

        // Kiểm tra GameOver: Nếu ngay khi xuất hiện mà không hợp lệ (bị đụng ngay lập tức)
        if (!isValidMove(currentPiece, currentX, currentY)) {
            isGameOver = true;
        }
    }

    public boolean isValidMove(int[][] piece, int futureX, int futureY) {
        for (int r = 0; r < piece.length; r++) {
            for (int c = 0; c < piece[r].length; c++) {
                if (piece[r][c] != 0) {
                    int boardX = futureX + c;
                    int boardY = futureY + r;
                    
                    // Nếu gạch còn nằm lơ lửng trên không (boardY < 0), cho phép di chuyển thoải mái
                    if (boardY < 0) {
                        // Chỉ cấm nếu bị vượt quá mép trái/phải màn hình
                        if (boardX < 0 || boardX >= COLS) return false;
                        continue; // Bỏ qua kiểm tra va chạm với board vì chưa vào trong board
                    }

                    // Nếu đã rớt vào bàn chơi, kiểm tra đụng tường hoặc đụng gạch cũ
                    if (boardX < 0 || boardX >= COLS || boardY >= ROWS) return false;
                    if (board[boardY][boardX] != 0) return false;
                }
            }
        }
        return true;
    }

    public void hardDrop() {
        if (isGameOver) return;
        while (isValidMove(currentPiece, currentX, currentY + 1)) {
            currentY++;
        }
        lockPiece();
    }

    public void moveDown() {
        if (isGameOver) return;
        if (isValidMove(currentPiece, currentX, currentY + 1)) {
            currentY++;
        } else {
            lockPiece();
        }
    }

    private void lockPiece() {
        // Kiểm tra Lock Out (Game Over do khối bị khóa kẹt khi chưa rơi hết vào bàn chơi)
        boolean isPieceOut = false;
        for (int r = 0; r < currentPiece.length; r++) {
            for (int c = 0; c < currentPiece[0].length; c++) {
                if (currentPiece[r][c] != 0 && (currentY + r) < 0) {
                    isPieceOut = true;
                    break;
                }
            }
        }
        if (isPieceOut) {
            isGameOver = true;
        } else {
            mergePieceToBoard();
        }
    }

    public void moveLeft() {
        if (isGameOver) return;
        if (isValidMove(currentPiece, currentX - 1, currentY)) currentX--;
    }

    public void moveRight() {
        if (isGameOver) return;
        if (isValidMove(currentPiece, currentX + 1, currentY)) currentX++;
    }

    public void Rotate90Clockwise() {
        if (isGameOver) return;
        int n = currentPiece.length;
        int[][] rotatedPiece = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                rotatedPiece[j][n - 1 - i] = currentPiece[i][j];
            }
        }

        int[][] offsets = {
                {0, 0},   // Thử vị trí hiện tại
                {1, 0},   // Đẩy sang phải 1 ô
                {-1, 0},  // Đẩy sang trái 1 ô
                {2, 0},   // Đẩy sang phải 2 ô (cho thanh I)
                {-2, 0},  // Đẩy sang trái 2 ô (cho thanh I)
                {0, -1}   // Đẩy lên trên 1 ô
        };

        for (int[] offset : offsets) {
            int newX = currentX + offset[0];
            int newY = currentY + offset[1];

            if (isValidMove(rotatedPiece, newX, newY)) {
                currentPiece = rotatedPiece;
                currentX = newX;
                currentY = newY;
                break;
            }
        }
    }

    private void mergePieceToBoard() {
        for (int r = 0; r < currentPiece.length; r++) {
            for (int c = 0; c < currentPiece[r].length; c++) {
                if (currentPiece[r][c] != 0) {
                    int boardY = currentY + r;
                    int boardX = currentX + c;
                    if (boardY >= 0 && boardY < ROWS && boardX >= 0 && boardX < COLS) {
                        board[boardY][boardX] = currentPiece[r][c];
                    }
                }
            }
        }
        isRowFull();
        spawnPiece();
    }

    private void isRowFull() {
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean isFull = true;
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == 0) {
                    isFull = false;
                    break;
                }
            }
            if (isFull) {
                if (lineClearListener != null) {
                    for (int c = 0; c < COLS; c++) {
                        lineClearListener.onLineCleared(r, c, board[r][c]);
                    }
                }
                for (int j = r; j > 0; j--) {
                    for (int i = 0; i < COLS; i++) {
                        board[j][i] = board[j - 1][i];
                    }
                }
                for (int i = 0; i < COLS; i++) {
                    board[0][i] = 0;
                }
                r++;
                score += 5;
            }
        }
    }

    public int[][] getBoard() { return board; }
    public int[][] getCurrentPiece() { return currentPiece; }
    public int[][] getNextPiece() { return nextPiece; }
    public int getCurrentX() { return currentX; }
    public int getCurrentY() { return currentY; }
    public int getScore() { return score; }
    public boolean isGameOver() { return isGameOver; }

    /** Independent data only: no Android objects or references to a live board. */
    public static final class State {
        public final int[][] board, currentPiece, nextPiece;
        public final int currentX, currentY, score, bagIndex;
        public final boolean gameOver;
        public final int[] bag;
        public final long randomState;

        public State(int[][] board, int[][] currentPiece, int[][] nextPiece,
                     int currentX, int currentY, int score, boolean gameOver,
                     int[] bag, int bagIndex, long randomState) {
            this.board = copy(board); this.currentPiece = copy(currentPiece); this.nextPiece = copy(nextPiece);
            this.currentX = currentX; this.currentY = currentY; this.score = score;
            this.gameOver = gameOver; this.bag = bag.clone(); this.bagIndex = bagIndex;
            this.randomState = randomState;
        }
    }

    public State exportState() {
        int[] savedBag = new int[bag.size()];
        for (int i = 0; i < savedBag.length; i++) savedBag[i] = bag.get(i);
        return new State(board, currentPiece, nextPiece, currentX, currentY, score,
                isGameOver, savedBag, currentBagIndex, random.state);
    }

    public static TetrisLogic fromState(State state) {
        validateMatrix(state.board, ROWS, COLS);
        TetrisLogic logic = new TetrisLogic(0, false);
        logic.validatePiece(state.currentPiece);
        logic.validatePiece(state.nextPiece);
        if (state.score < 0 || state.currentX < -4 || state.currentX >= COLS
                || state.currentY < -4 || state.currentY >= ROWS
                || state.bag.length != 7 || state.bagIndex < 0 || state.bagIndex > 7
                || state.randomState < 0 || state.randomState > StatefulRandom.MASK) {
            throw new IllegalArgumentException("Invalid Tetris state");
        }
        boolean[] found = new boolean[7];
        for (int piece : state.bag) {
            if (piece < 0 || piece >= 7 || found[piece]) throw new IllegalArgumentException("Invalid piece bag");
            found[piece] = true; logic.bag.add(piece);
        }
        for (int r = 0; r < ROWS; r++) System.arraycopy(state.board[r], 0, logic.board[r], 0, COLS);
        logic.currentPiece = copy(state.currentPiece); logic.nextPiece = copy(state.nextPiece);
        logic.currentX = state.currentX; logic.currentY = state.currentY;
        logic.score = state.score; logic.isGameOver = state.gameOver;
        logic.currentBagIndex = state.bagIndex; logic.random.state = state.randomState;
        if (!logic.isGameOver && !logic.isValidMove(logic.currentPiece, logic.currentX, logic.currentY)) {
            throw new IllegalArgumentException("Active piece overlaps the board");
        }
        return logic;
    }

    private static int[][] copy(int[][] matrix) {
        if (matrix == null) throw new IllegalArgumentException("Missing matrix");
        int[][] result = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null) throw new IllegalArgumentException("Missing matrix row");
            result[i] = matrix[i].clone();
        }
        return result;
    }

    private static void validateMatrix(int[][] matrix, int rows, int columns) {
        if (matrix == null || matrix.length != rows) throw new IllegalArgumentException("Invalid matrix height");
        for (int[] row : matrix) {
            if (row == null || row.length != columns) throw new IllegalArgumentException("Invalid matrix width");
            for (int cell : row) if (cell < 0 || cell > 7) throw new IllegalArgumentException("Invalid block");
        }
    }

    private void validatePiece(int[][] piece) {
        if (piece == null || piece.length < 2 || piece.length > 4) throw new IllegalArgumentException("Invalid piece size");
        validateMatrix(piece, piece.length, piece.length);
        for (int[][] original : ALL_SHAPES) {
            if (original.length != piece.length) continue;
            int[][] rotated = copy(original);
            for (int turn = 0; turn < 4; turn++) {
                if (Arrays.deepEquals(rotated, piece)) return;
                int n = rotated.length;
                int[][] next = new int[n][n];
                for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) next[c][n - 1 - r] = rotated[r][c];
                rotated = next;
            }
        }
        throw new IllegalArgumentException("Unknown tetromino");
    }

    /** Same algorithm as java.util.Random, with an explicit, checkpointable state. */
    private static final class StatefulRandom extends Random {
        private static final long serialVersionUID = 1L;
        private static final long MASK = (1L << 48) - 1;
        private long state;
        StatefulRandom(long seed) { super(seed); state = (seed ^ 0x5DEECE66DL) & MASK; }
        @Override protected int next(int bits) {
            state = (state * 0x5DEECE66DL + 0xBL) & MASK;
            return (int) (state >>> (48 - bits));
        }
    }
}
