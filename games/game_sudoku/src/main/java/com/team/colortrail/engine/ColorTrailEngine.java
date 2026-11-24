package com.team.colortrail.engine;

import java.util.Arrays;
import java.util.Random;

public final class ColorTrailEngine {
    public static final int SIDE = 5;
    public static final int SIZE = SIDE * SIDE;
    public static final int EXIT = SIZE - 1;
    public static final int REQUIRED_CRYSTALS = 3;
    private static final int INITIAL_ENERGY = 10;

    private final int[] colors = new int[SIZE];
    private final boolean[] crystals = new boolean[SIZE];
    private final int[] path = new int[SIZE - 1];
    private final long seed;
    private int pathLength;
    private int position;
    private int visited = 1;
    private int collected;
    private int energy = INITIAL_ENERGY;

    public ColorTrailEngine(long seed) {
        this.seed = seed;
        Random random = new Random(seed);
        for (int index = 0; index < SIZE; index++) {
            colors[index] = random.nextInt(4);
        }

        // Shuffle a guaranteed eight-step route, then color it and place three crystals.
        int[] directions = {1, 1, 1, 1, SIDE, SIDE, SIDE, SIDE};
        for (int index = directions.length - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            int direction = directions[index];
            directions[index] = directions[swap];
            directions[swap] = direction;
        }
        int cell = 0;
        for (int step = 0; step < directions.length; step++) {
            int neighbor = cell + directions[step];
            colors[neighbor] = (colors[cell] + 1 + random.nextInt(3)) % 4;
            if (step == 1 || step == 3 || step == 5) {
                crystals[neighbor] = true;
            }
            cell = neighbor;
        }
        int extra = random.nextInt(SIZE - 2) + 1;
        crystals[extra] = true;
    }

    public static ColorTrailEngine restore(long seed, int[] path) {
        if (path.length >= SIZE) {
            throw new IllegalArgumentException("Invalid path length");
        }
        ColorTrailEngine game = new ColorTrailEngine(seed);
        for (int step : path) {
            if (!game.move(step)) {
                throw new IllegalArgumentException("Invalid saved path");
            }
        }
        return game;
    }

    public long seed() {
        return seed;
    }

    public int[] path() {
        return Arrays.copyOf(path, pathLength);
    }

    public int position() {
        return position;
    }

    public int energy() {
        return energy;
    }

    public int collected() {
        return collected;
    }

    public int colorAt(int index) {
        return colors[index];
    }

    public boolean crystalAt(int index) {
        return crystals[index] && !visited(index);
    }

    public boolean visited(int index) {
        return (visited & (1 << index)) != 0;
    }

    public boolean canMove(int destination) {
        return !won() && energy > 0 && legal(position, destination, visited, collected);
    }

    public boolean move(int destination) {
        if (!canMove(destination)) {
            return false;
        }
        path[pathLength++] = destination;
        visited |= 1 << destination;
        position = destination;
        if (crystals[destination]) {
            collected++;
        } else {
            energy--;
        }
        return true;
    }

    public boolean won() {
        return position == EXIT;
    }

    public boolean ended() {
        if (won()) {
            return true;
        }
        for (int neighbor : neighbors(position)) {
            if (canMove(neighbor)) {
                return false;
            }
        }
        return true;
    }

    public int score() {
        return collected * 150 + energy * 25;
    }

    /** Find a feasible finish and choose a first step with the best finish score. */
    public int suggestMove() {
        int bestScore = -1;
        int bestMove = -1;
        for (int destination : neighbors(position)) {
            if (canMove(destination)) {
                int finishScore = scoreAfterMove(destination, visited, collected, energy);
                if (finishScore > bestScore) {
                    bestScore = finishScore;
                    bestMove = destination;
                }
            }
        }
        return bestMove;
    }

    private int scoreAfterMove(int cell, int visitedCells, int crystalsFound, int remaining) {
        int updatedVisited = visitedCells | (1 << cell);
        if (crystals[cell]) {
            crystalsFound++;
        } else {
            remaining--;
        }
        if (cell == EXIT) {
            return crystalsFound * 150 + remaining * 25;
        }
        if (remaining <= 0) {
            return -1;
        }
        int best = -1;
        for (int next : neighbors(cell)) {
            if (legal(cell, next, updatedVisited, crystalsFound)) {
                best = Math.max(best, scoreAfterMove(next, updatedVisited, crystalsFound, remaining));
            }
        }
        return best;
    }

    private boolean legal(int from, int to, int visitedCells, int crystalsFound) {
        if (to < 0 || to >= SIZE || (visitedCells & (1 << to)) != 0 || colors[from] == colors[to]) {
            return false;
        }
        int distance = Math.abs(from / SIDE - to / SIDE) + Math.abs(from % SIDE - to % SIDE);
        return distance == 1 && (to != EXIT || crystalsFound >= REQUIRED_CRYSTALS);
    }

    private static int[] neighbors(int cell) {
        int[] result = new int[4];
        int count = 0;
        if (cell >= SIDE) {
            result[count++] = cell - SIDE;
        }
        if (cell < SIZE - SIDE) {
            result[count++] = cell + SIDE;
        }
        if (cell % SIDE != 0) {
            result[count++] = cell - 1;
        }
        if (cell % SIDE != SIDE - 1) {
            result[count++] = cell + 1;
        }
        return Arrays.copyOf(result, count);
    }
}
