package gg.vape.utils.processors;

/**
 * A resumable cube scan. Horizontal columns are visited in expanding rings,
 * and each column is visited from the player's height outwards. A caller may
 * skip a whole column when its chunk is not loaded.
 */
public final class SearchScanCursor {
    private final int centerX, centerY, centerZ, radius;
    private int ring, column, vertical;
    private int x, y, z;

    public SearchScanCursor(int centerX, int centerY, int centerZ, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = Math.max(0, radius);
    }

    public boolean next() {
        if (this.vertical > this.radius * 2) {
            this.vertical = 0;
            ++this.column;
            int columnsInRing = this.ring == 0 ? 1 : this.ring * 8;
            if (this.column == columnsInRing) {
                ++this.ring;
                this.column = 0;
            }
        }
        if (this.ring > this.radius) return false;

        int dx = 0, dz = 0;
        if (this.ring != 0) {
            int side = this.ring * 2;
            int index = this.column;
            if (index <= side) {
                dx = -this.ring + index;
                dz = -this.ring;
            } else if ((index -= side + 1) < side) {
                dx = this.ring;
                dz = -this.ring + 1 + index;
            } else if ((index -= side) < side) {
                dx = this.ring - 1 - index;
                dz = this.ring;
            } else {
                index -= side;
                dx = -this.ring;
                dz = this.ring - 1 - index;
            }
        }
        int dy = this.vertical == 0 ? 0 : (this.vertical + 1) / 2 * (this.vertical % 2 == 1 ? 1 : -1);
        ++this.vertical;
        this.x = this.centerX + dx;
        this.y = this.centerY + dy;
        this.z = this.centerZ + dz;
        return true;
    }

    public void skipColumn() { this.vertical = this.radius * 2 + 1; }
    public int x() { return this.x; }
    public int y() { return this.y; }
    public int z() { return this.z; }
    public int centerX() { return this.centerX; }
    public int centerY() { return this.centerY; }
    public int centerZ() { return this.centerZ; }
    public int radius() { return this.radius; }
}
