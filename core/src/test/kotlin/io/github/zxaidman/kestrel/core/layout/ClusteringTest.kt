package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClusteringTest {

    private fun rect(cx: Double, cy: Double, size: Double) =
        PixelRect(centerX = cx, centerY = cy, width = size, height = size)

    private fun group(vararg placed: Pair<String, PixelRect>, gap: Double = 30.0) =
        Clustering.group(placed.toList(), gap)

    @Test
    fun `controls a thumb could roll between share a window`() {
        val clusters = group(
            "a" to rect(0.0, 0.0, 100.0),
            "b" to rect(120.0, 0.0, 100.0),
        )
        assertEquals(1, clusters.size)
        assertEquals(listOf("a", "b"), clusters.single().elementIds)
    }

    @Test
    fun `controls far enough apart get their own windows`() {
        val clusters = group(
            "a" to rect(0.0, 0.0, 100.0),
            "b" to rect(400.0, 0.0, 100.0),
        )
        assertEquals(2, clusters.size)
    }

    @Test
    fun `grouping is transitive, because a row of buttons is one row`() {
        // A and C are 240 apart with a gap of 140, far beyond the threshold. They still belong
        // together, because B bridges them and a thumb can travel the whole row.
        val clusters = group(
            "a" to rect(0.0, 0.0, 100.0),
            "b" to rect(120.0, 0.0, 100.0),
            "c" to rect(240.0, 0.0, 100.0),
        )
        assertEquals(1, clusters.size)
        assertEquals(listOf("a", "b", "c"), clusters.single().elementIds)
    }

    @Test
    fun `overlapping controls are always together`() {
        val clusters = group(
            "a" to rect(0.0, 0.0, 100.0),
            "b" to rect(20.0, 0.0, 100.0),
            gap = 0.0,
        )
        assertEquals(1, clusters.size)
    }

    @Test
    fun `a diamond of round buttons is one cluster, not four`() {
        // The case that makes bounding boxes the wrong measure: these boxes all overlap and the
        // circles all touch nowhere. Measured as circles they are close, which is correct.
        val spread = 92.0
        val clusters = group(
            "y" to rect(0.0, -spread, 112.0),
            "x" to rect(-spread, 0.0, 112.0),
            "b" to rect(spread, 0.0, 112.0),
            "a" to rect(0.0, spread, 112.0),
        )
        assertEquals(1, clusters.size)
        assertEquals(4, clusters.single().elementIds.size)
    }

    @Test
    fun `the enclosing rectangle covers every control in the cluster`() {
        val clusters = group(
            "a" to rect(0.0, 0.0, 100.0),
            "b" to rect(120.0, 20.0, 100.0),
        )
        val bounds = clusters.single().bounds
        assertEquals(-50.0, bounds.left)
        assertEquals(-50.0, bounds.top)
        assertEquals(170.0, bounds.right)
        assertEquals(70.0, bounds.bottom)
    }

    @Test
    fun `nothing placed produces nothing, rather than an empty rectangle somewhere`() {
        assertTrue(Clustering.group(emptyList(), 30.0).isEmpty())
    }

    @Test
    fun `one control is one cluster whose bounds are its own`() {
        val clusters = group("only" to rect(10.0, 20.0, 50.0))
        assertEquals(1, clusters.size)
        assertEquals(rect(10.0, 20.0, 50.0).left, clusters.single().bounds.left)
    }

    @Test
    fun `clusters come out in the order their first control was declared`() {
        val clusters = group(
            "far" to rect(1000.0, 0.0, 50.0),
            "near.a" to rect(0.0, 0.0, 50.0),
            "near.b" to rect(60.0, 0.0, 50.0),
        )
        assertEquals(listOf("far"), clusters[0].elementIds)
        assertEquals(listOf("near.a", "near.b"), clusters[1].elementIds)
    }

    // --- the property that made this necessary --------------------------------------------------

    @Test
    fun `the shipped layout groups the way the tested overlay behaved`() {
        // The arrangement people have actually played with: a stick shares its window with its own
        // press so a thumb can hold one and move the other, and the face buttons share theirs so a
        // thumb can roll between them. The pad and the sticks stay apart.
        val layout = (BuiltInLayouts.load(BuiltInLayouts.XBOX_DEFAULT) as Outcome.Success).value
        val surface = LayoutSurface(2400.0, 1080.0)
        val placed = layout.elements.map { it.id to it.placement.resolve(surface) }
        val clusters = Clustering.group(placed, Clustering.DEFAULT_GAP * surface.shortSide)

        fun clusterOf(id: String) = clusters.single { id in it.elementIds }.elementIds.toSet()

        assertEquals(setOf("stick.left", "stick.left.press"), clusterOf("stick.left"))
        assertEquals(setOf("stick.right", "stick.right.press"), clusterOf("stick.right"))
        assertEquals(setOf("face.a", "face.b", "face.x", "face.y"), clusterOf("face.a"))
        assertEquals(setOf("dpad"), clusterOf("dpad"))
    }

    @Test
    fun `no cluster of the shipped layout covers a quarter of the screen`() {
        // Every pixel of a window that is not a control is a pixel nothing can be touched through,
        // so a cluster growing to cover the screen is the failure this design exists to avoid.
        val layout = (BuiltInLayouts.load(BuiltInLayouts.XBOX_DEFAULT) as Outcome.Success).value
        val surface = LayoutSurface(2400.0, 1080.0)
        val placed = layout.elements.map { it.id to it.placement.resolve(surface) }
        val screen = surface.widthPx * surface.heightPx

        Clustering.group(placed, Clustering.DEFAULT_GAP * surface.shortSide).forEach { cluster ->
            val area = cluster.bounds.width * cluster.bounds.height
            assertTrue(
                area < screen * 0.25,
                "${cluster.elementIds} covers ${(area / screen * 100).toInt()}% of the screen",
            )
        }
    }

    @Test
    fun `the total window area is a small part of the screen`() {
        val layout = (BuiltInLayouts.load(BuiltInLayouts.XBOX_DEFAULT) as Outcome.Success).value
        val surface = LayoutSurface(2400.0, 1080.0)
        val placed = layout.elements.map { it.id to it.placement.resolve(surface) }
        val screen = surface.widthPx * surface.heightPx
        val covered = Clustering.group(placed, Clustering.DEFAULT_GAP * surface.shortSide)
            .sumOf { it.bounds.width * it.bounds.height }

        assertTrue(covered < screen * 0.35, "windows cover ${(covered / screen * 100).toInt()}%")
    }

    // --- scaling --------------------------------------------------------------------------------

    @Test
    fun `scaling shrinks the arrangement towards its anchor, not just the controls`() {
        val full = Placement(Anchor.BOTTOM_LEFT, 0.2, 0.2, 0.1, 0.1)
        val half = full.scaledBy(0.5)

        assertEquals(0.1, half.offsetX)
        assertEquals(0.1, half.offsetY)
        assertEquals(0.05, half.width)
        assertEquals(0.05, half.height)
    }

    @Test
    fun `a scaled layout keeps every control the same distance from the edge in its own terms`() {
        // The property that matters: a control's clearance from the edge, measured in its own
        // widths, does not change with the setting.
        val surface = LayoutSurface(2400.0, 1080.0)
        val full = Placement(Anchor.BOTTOM_LEFT, 0.2, 0.2, 0.1, 0.1)
        listOf(1.0, 0.65, 0.35).forEach { factor ->
            val rect = full.scaledBy(factor).resolve(surface)
            assertEquals(
                1.5,
                (rect.left - 0.0) / rect.width,
                1e-9,
                "clearance changed at scale $factor",
            )
        }
    }
}
