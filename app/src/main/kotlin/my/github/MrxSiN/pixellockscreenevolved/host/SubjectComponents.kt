package my.github.MrxSiN.pixellockscreenevolved.host

/**
 * The separate subjects in one mask, for keeping only the main one
 * ([DepthSubjects.MAIN]): two people a step apart, a person and a dog, a
 * lamp post behind someone.
 *
 * The mask is looked at on a coarse grid ([GRID] cells along its long side),
 * where a cell is ink when any of its pixels is solid enough ([INK]); ink cells
 * touching, corners included, are one subject. The subject with the most alpha
 * is kept, with a cell's margin so its soft edge is not cut, and the others
 * cleared. Kept free of Android so it can be unit tested.
 */
internal object SubjectComponents {

    private const val GRID = 64
    private const val INK = 128

    /** [alpha], [width] by [height], from 0 to 255, with every subject but the main one cleared, in place. */
    fun mainOnly(alpha: IntArray, width: Int, height: Int): IntArray {
        val grid = Grid(alpha, width, height)
        if (grid.components <= 1) return alpha
        val kept = grid.weights.indices.maxBy { grid.weights[it] } + 1
        val keep = BooleanArray(grid.labels.size)
        for (cy in 0 until grid.rows) for (cx in 0 until grid.columns) {
            if (grid.labels[cy * grid.columns + cx] != kept) continue
            for (dy in -1..1) for (dx in -1..1) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until grid.columns && y in 0 until grid.rows) keep[y * grid.columns + x] = true
            }
        }
        for (y in 0 until height) for (x in 0 until width) {
            if (!keep[(y / grid.cell) * grid.columns + x / grid.cell]) alpha[y * width + x] = 0
        }
        return alpha
    }

    private class Grid(alpha: IntArray, width: Int, height: Int) {
        val cell = maxOf(1, (maxOf(width, height) + GRID - 1) / GRID)
        val columns = (width + cell - 1) / cell
        val rows = (height + cell - 1) / cell

        /** The subject each cell belongs to, from 1, or 0 for none. */
        val labels = IntArray(columns * rows)

        /** The alpha of each subject, by its label less one. */
        val weights: LongArray
        val components: Int

        init {
            val inked = BooleanArray(labels.size)
            val sums = LongArray(labels.size)
            for (y in 0 until height) for (x in 0 until width) {
                val a = alpha[y * width + x]
                val c = (y / cell) * columns + x / cell
                sums[c] += a
                if (a >= INK) inked[c] = true
            }
            val found = ArrayList<Long>()
            val stack = IntArray(labels.size)
            for (start in labels.indices) {
                if (!inked[start] || labels[start] != 0) continue
                val label = found.size + 1
                var weight = 0L
                var top = 0
                stack[top++] = start
                labels[start] = label
                while (top > 0) {
                    val c = stack[--top]
                    weight += sums[c]
                    val cx = c % columns
                    val cy = c / columns
                    for (dy in -1..1) for (dx in -1..1) {
                        val x = cx + dx
                        val y = cy + dy
                        if (x !in 0 until columns || y !in 0 until rows) continue
                        val n = y * columns + x
                        if (inked[n] && labels[n] == 0) {
                            labels[n] = label
                            stack[top++] = n
                        }
                    }
                }
                found += weight
            }
            weights = found.toLongArray()
            components = found.size
        }
    }
}
