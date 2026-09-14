package emu.grasscutter.game.world;

import com.github.davidmoten.rtreemulti.geometry.Point;
import dev.morphia.annotations.Entity;
import java.io.*;
import java.util.List;
import lombok.*;

@Entity
public final class GridPosition implements Serializable {
    private static final long serialVersionUID = -2001232300615923575L;

    @Getter @Setter private int x;

    @Getter @Setter private int z;

    @Getter @Setter private int width;

    public GridPosition() {}

    public GridPosition(int x, int y, int width) {
        set(x, y, width);
    }

    public GridPosition(GridPosition pos) {
        this.set(pos);
    }

    public GridPosition(Position pos, int width) {
        this.set((int) (pos.getX() / width), (int) (pos.getZ() / width), width);
    }

    public GridPosition(List<Integer> xzwidth) {
        this.width = xzwidth.get(2);
        this.z = xzwidth.get(1);
        this.x = xzwidth.get(0);
    }

    @SneakyThrows
    public GridPosition(String str) {
        var listOfParams = str.replace(" ", "").replace("(", "").replace(")", "").split(",");
        if (listOfParams.length != 3)
            throw new IOException("invalid size on GridPosition definition - ");
        try {
            this.x = Integer.parseInt(listOfParams[0]);
            this.z = Integer.parseInt(listOfParams[1]);
            this.width = Integer.parseInt(listOfParams[2]);
        } catch (NumberFormatException ignored) {
            throw new IOException("invalid number on GridPosition definition - ");
        }
    }

    public GridPosition set(int x, int z) {
        this.x = x;
        this.z = z;
        return this;
    }

    public GridPosition set(int x, int z, int width) {
        this.x = x;
        this.z = z;
        this.width = width;
        return this;
    }

    // Deep copy
    public GridPosition set(GridPosition pos) {
        return this.set(pos.getX(), pos.getZ(), pos.getWidth());
    }

    public GridPosition addClone(int x, int z) {
        GridPosition pos = clone();
        pos.x += x;
        pos.z += z;
        return pos;
    }

    @Override
    public GridPosition clone() {
        return new GridPosition(x, z, width);
    }

    @Override
    public String toString() {
        return "(" + this.getX() + ", " + this.getZ() + ", " + this.getWidth() + ")";
    }

    public int[] toIntArray() {
        return new int[] {x, z, width};
    }

    public double[] toDoubleArray() {
        return new double[] {x, z};
    }

    public int[] toXZIntArray() {
        return new int[] {x, z};
    }

    public Point toPoint() {
        return Point.create(x, z);
    }

    @Override
    public int hashCode() {
        /*
         * Spatial hash for grid cells.
         *
         * The previous 31-based mixing gave the same value to whole families of cells: with a grid
         * width of 20 the cells (x, z) and (x + 20, z - 620) hash alike, and a scene has many such
         * pairs. The grid maps are large (scene 3 has ~38k cells) and are read from the cached grid
         * file, so every one of those collisions turned into a tree bin that has to be walked on
         * each insert: reading cache/scene3_grid.json took ~28 seconds because of it, and the world
         * tick that does that read blocked logins for as long as it ran. Three large primes spread
         * the cells out instead and the same read now takes ~0.5 seconds.
         */
        return (x * 73856093) ^ (z * 19349663) ^ (width * 83492791);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (getClass() != o.getClass()) return false;
        GridPosition pos = (GridPosition) o;
        // field comparison
        return pos.x == x && pos.z == z && pos.width == width;
    }
}
