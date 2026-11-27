package my.github.MrxSiN.pixellockscreenevolved.depth

/**
 * He, Sun and Tang's guided filter, grey guide: smooths [input] everywhere
 * except where [guide] has an edge, so a mask the model found soft and a few
 * pixels off snaps to the photo's own outline (hair, shoulders, fingers).
 *
 * Every box mean is a separable running sum, a row pass then a column pass, so
 * the cost is linear in the pixels whatever the radius, and the filter holds
 * five images of floats besides its input and guide: it runs in SystemUI too,
 * where a summed-area table of doubles and an image per step had cost some
 * 50 MB for a 1024 px photo. Kept free of Android so it can be unit tested.
 */
internal object GuidedFilter {

    /**
     * [input] filtered by [guide], both [width] by [height], row by row, values
     * from 0 to 1. [epsilon] is how strong an edge in the guide must be to hold
     * the output back from smoothing across it: smaller follows the guide closer.
     */
    fun apply(guide: FloatArray, input: FloatArray, width: Int, height: Int, radius: Int, epsilon: Float): FloatArray {
        val size = width * height
        require(guide.size == size && input.size == size) { "guide and input must be width × height" }
        val scratch = FloatArray(size)
        val columns = DoubleArray(width)
        val meanI = boxMean(guide, FloatArray(size), scratch, columns, width, height, radius)
        val meanP = boxMean(input, FloatArray(size), scratch, columns, width, height, radius)
        val a = FloatArray(size).also { for (i in it.indices) it[i] = guide[i] * guide[i] }
        val b = FloatArray(size).also { for (i in it.indices) it[i] = guide[i] * input[i] }
        boxMean(a, a, scratch, columns, width, height, radius)
        boxMean(b, b, scratch, columns, width, height, radius)
        for (i in 0 until size) {
            val variance = a[i] - meanI[i] * meanI[i]
            val covariance = b[i] - meanI[i] * meanP[i]
            val slope = covariance / (variance + epsilon)
            a[i] = slope
            b[i] = meanP[i] - slope * meanI[i]
        }
        boxMean(a, a, scratch, columns, width, height, radius)
        boxMean(b, b, scratch, columns, width, height, radius)
        // The answer goes into what held the guide's mean, which is no longer needed.
        for (i in 0 until size) meanI[i] = (a[i] * guide[i] + b[i]).coerceIn(0f, 1f)
        return meanI
    }

    /** The mean of [values] over the box [radius] pixels about each pixel, clipped at the edges. */
    fun boxMean(values: FloatArray, width: Int, height: Int, radius: Int): FloatArray =
        boxMean(values, FloatArray(values.size), FloatArray(values.size), DoubleArray(width), width, height, radius)

    /**
     * [boxMean] into [out], which may be [values] itself, with [scratch] the size of [values] and
     * [columns] [width] long to work in. Sums run in doubles: a float running sum over a 1024 px
     * row drifts by more than a small box's mean.
     */
    private fun boxMean(
        values: FloatArray,
        out: FloatArray,
        scratch: FloatArray,
        columns: DoubleArray,
        width: Int,
        height: Int,
        radius: Int,
    ): FloatArray {
        // Along each row: the mean of the values within radius, the count clipped at the row's ends.
        for (y in 0 until height) {
            val row = y * width
            var sum = 0.0
            for (x in 0 until minOf(radius, width)) sum += values[row + x]
            for (x in 0 until width) {
                if (x + radius < width) sum += values[row + x + radius]
                if (x - radius - 1 >= 0) sum -= values[row + x - radius - 1]
                scratch[row + x] = (sum / (minOf(width - 1, x + radius) - maxOf(0, x - radius) + 1)).toFloat()
            }
        }
        // Down each column: the same over the row means, a running sum per column walked a row at a time.
        columns.fill(0.0)
        for (y in 0 until minOf(radius, height)) {
            val row = y * width
            for (x in 0 until width) columns[x] += scratch[row + x]
        }
        for (y in 0 until height) {
            if (y + radius < height) {
                val row = (y + radius) * width
                for (x in 0 until width) columns[x] += scratch[row + x]
            }
            if (y - radius - 1 >= 0) {
                val row = (y - radius - 1) * width
                for (x in 0 until width) columns[x] -= scratch[row + x]
            }
            val count = minOf(height - 1, y + radius) - maxOf(0, y - radius) + 1
            val row = y * width
            for (x in 0 until width) out[row + x] = (columns[x] / count).toFloat()
        }
        return out
    }
}
