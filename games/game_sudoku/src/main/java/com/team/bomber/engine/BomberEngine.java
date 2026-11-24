package com.team.bomber.engine;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class BomberEngine {
    public static final int SIDE = 9;
    public static final int AREA = SIDE * SIDE;
    public static final int FLOOR = 0;
    public static final int WALL = 1;
    public static final int CRATE = 2;
    public static final int CRYSTAL_CRATE = 3;
    public static final int CRYSTAL = 4;
    public static final int EXIT = 5;
    public static final int START = 10;
    public static final int DOOR = 70;
    public static final int REQUIRED = 3;
    public static final int LIMIT = 80;

    private static final int FUSE = 3;
    private static final int RANGE = 2;
    private static final int[] OFFSETS = {-SIDE, SIDE, -1, 1};

    private final int[] tiles;
    private final int[] bombs;
    private final Set<Integer> flash = new HashSet<>();
    private int player;
    private int turns;
    private int crystals;
    private int score;
    private boolean lost;
    private boolean won;

    public BomberEngine(long seed) {
        tiles = new int[AREA];
        bombs = new int[AREA];
        player = START;
        Random random = new Random(seed);
        for (int row = 0; row < SIDE; row++) {
            for (int col = 0; col < SIDE; col++) {
                if (row == 0 || col == 0 || row == SIDE - 1 || col == SIDE - 1
                        || (row % 2 == 0 && col % 2 == 0)) {
                    tiles[row * SIDE + col] = WALL;
                }
            }
        }
        for (int index : new int[]{23, 33, 49, 57}) {
            tiles[index] = CRYSTAL_CRATE;
        }
        for (int index : new int[]{14, 19, 25, 29, 32, 39, 43, 47, 51, 59, 65, 68}) {
            if (tiles[index] == FLOOR && random.nextBoolean()) {
                tiles[index] = CRATE;
            }
        }
        tiles[DOOR] = EXIT;
    }

    public BomberEngine(int[] tiles, int[] bombs, int player, int turns, int crystals, int score) {
        if (tiles.length != AREA || bombs.length != AREA || player < 0 || player >= AREA
                || turns < 0 || turns >= LIMIT || crystals < 0 || crystals > 4 || score < 0
                || tiles[DOOR] != EXIT || isBlocked(tiles[player])) {
            throw new IllegalArgumentException("Invalid Bomber checkpoint");
        }
        for (int index = 0; index < AREA; index++) {
            int tile = tiles[index];
            int row = index / SIDE;
            int col = index % SIDE;
            boolean border = row == 0 || col == 0 || row == SIDE - 1 || col == SIDE - 1;
            if (tile < FLOOR || tile > EXIT || (tile == EXIT && index != DOOR)
                    || (border && tile != WALL) || bombs[index] < 0 || bombs[index] > FUSE
                    || (bombs[index] > 0 && isBlocked(tile))) {
                throw new IllegalArgumentException("Invalid board");
            }
        }
        this.tiles = tiles.clone();
        this.bombs = bombs.clone();
        this.player = player;
        this.turns = turns;
        this.crystals = crystals;
        this.score = score;
    }

    public boolean move(int target) {
        if (ended() || target < 0 || target >= AREA || isBlocked(tiles[target]) || bombs[target] > 0) {
            return false;
        }
        int distance = Math.abs(target / SIDE - player / SIDE) + Math.abs(target % SIDE - player % SIDE);
        if (distance != 1) {
            return false;
        }
        player = target;
        if (tiles[target] == CRYSTAL) {
            crystals++;
            score += 120;
            tiles[target] = FLOOR;
        }
        advance();
        return true;
    }

    public boolean place() {
        if (ended() || bombs[player] > 0) {
            return false;
        }
        advance();
        if (!ended()) {
            bombs[player] = FUSE;
        }
        return true;
    }

    public boolean waitTurn() {
        if (ended()) {
            return false;
        }
        advance();
        return true;
    }

    private void advance() {
        turns++;
        flash.clear();
        ArrayDeque<Integer> detonating = new ArrayDeque<>();
        for (int index = 0; index < AREA; index++) {
            if (bombs[index] > 0) {
                bombs[index]--;
                if (bombs[index] == 0) {
                    detonating.add(index);
                }
            }
        }
        while (!detonating.isEmpty()) {
            burst(detonating.removeFirst(), detonating);
        }
        if (flash.contains(player)) {
            lost = true;
        } else if (player == DOOR && crystals >= REQUIRED) {
            won = true;
            score += 200 + (LIMIT - turns) * 5;
        } else if (turns >= LIMIT) {
            lost = true;
        }
    }

    private void burst(int origin, ArrayDeque<Integer> detonating) {
        flash.add(origin);
        for (int offset : OFFSETS) {
            for (int distance = 1; distance <= RANGE; distance++) {
                int index = origin + offset * distance;
                if (tiles[index] == WALL) {
                    break;
                }
                flash.add(index);
                if (bombs[index] > 0) {
                    bombs[index] = 0;
                    detonating.add(index);
                }
                if (tiles[index] == CRATE) {
                    tiles[index] = FLOOR;
                    score += 30;
                    break;
                }
                if (tiles[index] == CRYSTAL_CRATE) {
                    tiles[index] = CRYSTAL;
                    score += 30;
                    break;
                }
            }
        }
    }

    private static boolean isBlocked(int tile) {
        return tile == WALL || tile == CRATE || tile == CRYSTAL_CRATE;
    }

    public int cell(int index) { return tiles[index]; }
    public int fuse(int index) { return bombs[index]; }
    public boolean flashed(int index) { return flash.contains(index); }
    public int player() { return player; }
    public int crystals() { return crystals; }
    public int turns() { return turns; }
    public int score() { return score; }
    public boolean won() { return won; }
    public boolean ended() { return won || lost; }
}
