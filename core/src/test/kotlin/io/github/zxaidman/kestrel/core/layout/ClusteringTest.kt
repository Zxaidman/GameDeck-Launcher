package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClusteringTest {

    private fun rect(cx: Double, cy: Double, size: Double) =
        PixelRect(centerX = cx, centerY = cy, width = size, height = size)

    private fun group(vararg placed: Triple<String, String?, PixelRect>) = Clustering.group(
        placed.map { it.first to it.third },
        placed.associate { it.first to it.second },
    )

    @Test
    fun `controls in the same group share a window`() {
        val clusters = group(
            Triple("a", "face", rect(0.0, 0.0, 100.0)),
            Triple("b", "face", rect(400.0, 0.0, 100.0)),
        )
        assertEquals(1, clusters.size)
        assertEquals(listOf("a", "b"), clusters.single().elementIds)
    }

    @Test
    fun `distance does not decide anything`() {
        // The whole point of declaring it: two controls drawn touching are still separate windows
        // if the layout says so, and two drawn far apart still share one if it says that.
        val touching = group(
            Triple("a", null, rect(0.0, 0.0, 100.0)),
            Triple("b", null, rect(60.0, 0.0, 100.0)),
        )
        assertEquals(2, touching.size)
    }

    @Test
    fun `a control with no group gets a window of its own`() {
        val clusters = group(
            Triple("a", "face", rect(0.0, 0.0, 100.0)),
            Triple("lonely", null, rect(50.0, 0.0, 100.0)),
            Triple("b", "face", rect(100.0, 0.0, 100.0)),
        )
        assertEquals(2, clusters.size)
        assertEquals(listOf("a", "b"), clusters[0].elementIds)
        assertEquals(listOf("lonely"), clusters[1].elementIds)
    }

    @Test
    fun `the enclosing rectangle covers every control in the cluster`() {
        val clusters = group(
            Triple("a", "g", rect(0.0, 0.0, 100.0)),
            Triple("b", "g", rect(120.0, 20.0, 100.0)),
        )
        val bounds = clusters.single().bounds
        assertEquals(-50.0, bounds.left)
        assertEquals(-50.0, bounds.top)
        assertEquals(170.0, bounds.right)
        assertEquals(70.0, bounds.bottom)
    }

    @Test
    fun `nothing placed produces nothing, rather than an empty rectangle somewhere`() {
        assertTrue(Clustering.group(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `clusters come out in the order their first control was declared`() {
        val clusters = group(
            Triple("far", null, rect(1000.0, 0.0, 50.0)),
            Triple("near.a", "pair", rect(0.0, 0.0, 50.0)),
            Triple("near.b", "pair", rect(60.0, 0.0, 50.0)),
        )
        assertEquals(listOf("far"), clusters[0].elementIds)
        assertEquals(listOf("near.a", "near.b"), clusters[1].elementIds)
    }

    @Test
    fun `an id that happens to match a group name does not merge them`() {
        val clusters = group(
            Triple("face", null, rect(0.0, 0.0, 50.0)),
            Triple("other", "face", rect(60.0, 0.0, 50.0)),
        )
        assertEquals(2, clusters.size)
    }

    // --- the shipped layout ---------------------------------------------------------------------

    private fun shipped(scale: Double, surface: LayoutSurface): List<Cluster> {
        val layout = (BuiltInLayouts.load(BuiltInLayouts.XBOX_DEFAULT) as Outcome.Success).value
        val placed = layout.elements.map { it.id to it.placement.scaledBy(scale).resolve(surface) }
        return Clustering.group(layout, placed)
    }

    @Test
    fun `the shipped layout groups the way the tested overlay behaved`() {
        // A stick shares its window with its own press, so a thumb can hold one and move the other.
        // The face buttons share theirs, so a thumb can roll between them. Each shoulder row keeps
        // its menu button, which is where they were when people played with them.
        val clusters = shipped(1.0, LayoutSurface(2400.0, 1080.0))
        fun clusterOf(id: String) = clusters.single { id in it.elementIds }.elementIds.toSet()

        assertEquals(setOf("stick.left", "stick.left.press"), clusterOf("stick.left"))
        assertEquals(setOf("stick.right", "stick.right.press"), clusterOf("stick.right"))
        assertEquals(setOf("face.a", "face.b", "face.x", "face.y"), clusterOf("face.a"))
        assertEquals(setOf("shoulder.l1", "shoulder.l2", "menu.select"), clusterOf("shoulder.l1"))
        assertEquals(setOf("shoulder.r1", "shoulder.r2", "menu.start"), clusterOf("shoulder.r1"))
        assertEquals(setOf("dpad"), clusterOf("dpad"))
    }

    @Test
    fun `grouping does not change with the size or the orientation`() {
        // The fault this prevents: holding L3 and moving the stick working at one size and not the
        // next. What a gesture does must not depend on how big the controls are.
        val shapes = listOf(LayoutSurface(2400.0, 1080.0), LayoutSurface(1080.0, 2400.0))
            .flatMap { surface -> listOf(0.40, 0.60, 0.85, 1.00).map { surface to it } }
            .map { (surface, scale) -> shipped(scale, surface).map { it.elementIds }.toSet() }
        assertEquals(1, shapes.toSet().size, "the grouping changed: $shapes")
    }

    @Test
    fun `no window of the shipped layout covers a quarter of the screen`() {
        // Every pixel of a window that is not a control is a pixel nothing can be touched through,
        // so a window growing to cover the screen is the failure this design exists to avoid.
        val surface = LayoutSurface(2400.0, 1080.0)
        val screen = surface.widthPx * surface.heightPx
        listOf(0.40, 0.85, 1.00).forEach { scale ->
            shipped(scale, surface).forEach { cluster ->
                val area = cluster.bounds.width * cluster.bounds.height
                assertTrue(
                    area < screen * 0.25,
                    "${cluster.elementIds} covers ${(area / screen * 100).toInt()}% at $scale",
                )
            }
        }
    }

    @Test
    fun `the windows together cover a small part of the screen`() {
        val surface = LayoutSurface(2400.0, 1080.0)
        val screen = surface.widthPx * surface.heightPx
        listOf(0.40, 0.85, 1.00).forEach { scale ->
            val covered = shipped(scale, surface).sumOf { it.bounds.width * it.bounds.height }
            assertTrue(
                covered < screen * 0.40,
                "windows cover ${(covered / screen * 100).toInt()}% at $scale",
            )
        }
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
        val surface = LayoutSurface(2400.0, 1080.0)
        val full = Placement(Anchor.BOTTOM_LEFT, 0.2, 0.2, 0.1, 0.1)
        listOf(1.0, 0.85, 0.40).forEach { factor ->
            val rect = full.scaledBy(factor).resolve(surface)
            assertEquals(1.5, (rect.left - 0.0) / rect.width, 1e-9, "clearance changed at $factor")
        }
    }
}
