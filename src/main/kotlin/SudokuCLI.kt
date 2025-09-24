@file:JvmName("SudokuApp")

package dk.marcusrokatis

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.openpdf.text.*
import org.openpdf.text.pdf.*
import java.io.File

class SudokuCLI : CliktCommand(name = "sudoku-cli") {
    override fun help(context: Context) = "Generér Sudoku-PDF (single, samurai, plus4)"
    private val mode by option("--mode", "-m", help = "single | samurai | plus4 | samurai-dual | plus4-dual")
        .choice("single", "samurai", "plus4", "multi", "samurai-dual", "plus4-dual")
        .default("plus4-dual")

    private val out by option("--out", "-o", help = "Output PDF-fil").default("sudoku.pdf")

    private val seed by option("--seed", help = "RNG seed").long().default(System.nanoTime())
    private val minGivens by option("--min-givens", help = "Minimum globale givens").int().default(26)
    private val includeSolution by option("--solution-page", help = "Tilføj ekstra side med løsning").flag(default = false)

    // Kun relevant for single/generisk
    private val n by option("--n", help = "N (fx 9 for 9x9, 4 for 4x4)").int().default(9)
    private val boxRows by option("--box-rows", help = "Boks-rækker (3 for 9x9, 2 for 4x4)").int().default(3)
    private val boxCols by option("--box-cols", help = "Boks-kolonner (3 for 9x9, 2 for 4x4)").int().default(3)

    // kun til multi:
    private val rows by option("--rows", help = "Antal rækker i multi-layout").int().default(3)
    private val cols by option("--cols", help = "Antal kolonner i multi-layout").int().default(2)
    private val gapCells by option("--gap-cells", help = "Tomt mellemrum (i celler) mellem brætter").int().default(3)
    private val varySeeds by option("--vary-seeds", help = "Brug forskelligt seed for hvert bræt").flag(default = true)

    override fun run() {
        val box = BoxSpec(boxRows, boxCols)
        val (finalBox, boards) = when (mode) {
            "single" -> {
                require(n == box.boxRows * box.boxCols) { "--n ($n) skal være boxRows*boxCols (${box.boxRows * box.boxCols})" }
                val givens = emptyNxN(n)
                box to listOf(BoardSpec(0, 0, givens))
            }
            "samurai" -> {
                val tl = emptyNxN(9); val tr = emptyNxN(9); val cc = emptyNxN(9); val bl = emptyNxN(9); val br = emptyNxN(9)
                samuraiLayout(tl, tr, cc, bl, br)
            }
            "plus4" -> {
                val up = emptyNxN(9); val left = emptyNxN(9); val right = emptyNxN(9); val down = emptyNxN(9)
                plus4Layout(up, left, right, down)
            }
            "samurai-dual" -> {
                val tl = emptyNxN(9); val tr = emptyNxN(9); val cc = emptyNxN(9); val bl = emptyNxN(9); val br = emptyNxN(9)
                val (box9, boards9) = samuraiLayout(tl, tr, cc, bl, br)
                val dualBoards = duplicateStackedVertical(box9, boards9, gapCells)

                val generated = MultiSudokuGenerator.generate(
                    box = box9,
                    boards = dualBoards,
                    seed = seed,
                    minGlobalGivens = minGivens
                )

                writePuzzlePdf(
                    outFile = File(out),
                    box = generated.box,
                    boards = generated.boards,
                    solutionGlobal = generated.solutionGlobal,
                    includeSolutionPage = includeSolution,
                    variantTitle = "Samurai (dual)"
                )
                echo("Skrev: ${File(out).absolutePath}")
                return
            }
            "plus4-dual" -> {
                val up = emptyNxN(9); val left = emptyNxN(9); val right = emptyNxN(9); val down = emptyNxN(9)
                val (box9, boards9) = plus4Layout(up, left, right, down)
                val dualBoards = duplicateStackedVertical(box9, boards9, gapCells)

                val generated = MultiSudokuGenerator.generate(
                    box = box9,
                    boards = dualBoards,
                    seed = seed,
                    minGlobalGivens = minGivens
                )

                writePuzzlePdf(
                    outFile = File(out),
                    box = generated.box,
                    boards = generated.boards,
                    solutionGlobal = generated.solutionGlobal,
                    includeSolutionPage = includeSolution,
                    variantTitle = "Plus4 (dual)"
                )
                echo("Skrev: ${File(out).absolutePath}")
                return
            }
            "multi" -> {
                require(n == box.boxRows * box.boxCols) { "--n ($n) skal være boxRows*boxCols (${box.boxRows * box.boxCols})" }
                val multi = buildMultiSingles(
                    rows = rows, cols = cols,
                    box = box,
                    seed = seed,
                    minGivens = minGivens,
                    gapCells = gapCells,
                    varySeeds = varySeeds
                )

                // skriv PDF – samme writer som før
                writePuzzlePdf(
                    outFile = File(out),
                    box = multi.box,
                    boards = multi.boards,
                    solutionGlobal = multi.solutionGlobal,
                    includeSolutionPage = includeSolution
                )
                echo("Skrev: ${File(out).absolutePath}")
                return
            }
            else -> error("Ukendt mode")
        }

        val generated = MultiSudokuGenerator.generate(
            box = finalBox,
            boards = boards,
            seed = seed,
            minGlobalGivens = minGivens
        )

        writePuzzlePdf(
            outFile = File(out),
            box = generated.box,
            boards = generated.boards,
            solutionGlobal = generated.solutionGlobal,
            includeSolutionPage = includeSolution
        )

        echo("Skrev: ${File(out).absolutePath}")
    }
}

fun main(args: Array<String>) = SudokuCLI().main(args)

/* ======================= Flere af single sudoku ======================= */

private data class MultiSingles(
    val box: BoxSpec,
    val boards: List<BoardSpec>,                      // opgaven (givens) for alle brætter lagt i gitter
    val solutionGlobal: Map<Pair<Int,Int>, Int>,      // samlet løsning mappet til globale (row, col)
    val rows: Int,
    val cols: Int
)

/** Generér R×C uafhængige single-sudokuer og placér dem i et tiled gitter. */
private fun buildMultiSingles(
    rows: Int,
    cols: Int,
    box: BoxSpec,
    seed: Long,
    minGivens: Int,
    gapCells: Int,
    varySeeds: Boolean
): MultiSingles {
    require(rows > 0 && cols > 0)
    val N = box.N
    val boardsOut = mutableListOf<BoardSpec>()
    val solutionOut = mutableMapOf<Pair<Int,Int>, Int>()

    // Størrelse per “flise” i globale celler (inkl. mellemrum)
    val tileH = N + gapCells
    val tileW = N + gapCells

    var idx = 0
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val thisSeed = if (varySeeds) seed + idx else seed
            idx++

            // generér en enkelt “single”
            val singleGivens = emptyNxN(N)
            val singleBoards = listOf(BoardSpec(0,0, singleGivens))
            val generated = MultiSudokuGenerator.generate(
                box = box,
                boards = singleBoards,
                seed = thisSeed,
                minGlobalGivens = minGivens
            )

            // offset i det globale canvas
            val offR = r * tileH
            val offC = c * tileW

            // flyt givens til deres offset og put dem i out-boards
            val placedGivens = Array(N) { IntArray(N) }
            for (rr in 0 until N) for (cc in 0 until N) {
                placedGivens[rr][cc] = generated.boards[0].givens[rr][cc]
            }
            boardsOut += BoardSpec(offR, offC, placedGivens)

            // flyt løsningen over i globalt koordinatsystem
            for ((pos, v) in generated.solutionGlobal) {
                val (gr, gc) = pos
                solutionOut[(gr + offR) to (gc + offC)] = v
            }
        }
    }
    return MultiSingles(box, boardsOut, solutionOut, rows, cols)
}

/** Duplikerer et helt layout side-om-side (vandret) med et mellemrum i celler. */
private fun duplicateSideBySide(box: BoxSpec, boards: List<BoardSpec>, gapCells: Int): List<BoardSpec> {
    // Find samlet bredde i celler for det eksisterende layout
    val minCol = boards.minOf { it.offsetCol }
    val maxCol = boards.maxOf { it.offsetCol + box.N - 1 }
    val compositeWidth = maxCol - minCol + 1
    val dx = compositeWidth + gapCells

    // Første sæt: som de er
    val first = boards.map { b ->
        // lav en dyb-kopi af givens så vi ikke deler arrays utilsigtet
        val g = Array(box.N) { r -> IntArray(box.N) { c -> b.givens[r][c] } }
        BoardSpec(b.offsetRow, b.offsetCol, g)
    }
    // Andet sæt: samme brætter, men forskudt i kolonner
    val second = boards.map { b ->
        val g = Array(box.N) { IntArray(box.N) } // tomme givens; de bliver udfyldt af generatoren
        BoardSpec(b.offsetRow, b.offsetCol + dx, g)
    }
    return first + second
}

/** Duplikerer et helt layout over/under (vertikalt) med et mellemrum i celler. */
private fun duplicateStackedVertical(box: BoxSpec, boards: List<BoardSpec>, gapCells: Int): List<BoardSpec> {
    // Find samlet højde i celler for det eksisterende layout
    val minRow = boards.minOf { it.offsetRow }
    val maxRow = boards.maxOf { it.offsetRow + box.N - 1 }
    val compositeHeight = maxRow - minRow + 1
    val dy = compositeHeight + gapCells

    // Første sæt: som de er
    val first = boards.map { b ->
        val g = Array(box.N) { r -> IntArray(box.N) { c -> b.givens[r][c] } }
        BoardSpec(b.offsetRow, b.offsetCol, g)
    }
    // Andet sæt: samme brætter, men forskudt i rækker
    val second = boards.map { b ->
        val g = Array(box.N) { IntArray(box.N) }
        BoardSpec(b.offsetRow + dy, b.offsetCol, g)
    }
    return first + second
}

/* ======================= PDF-rendering (OpenPDF) ======================= */

private data class PageLayout(
    val pageSize: Rectangle = PageSize.A4,
    val marginLeft: Float = 36f,
    val marginRight: Float = 36f,
    val marginTop: Float = 36f,
    val marginBottom: Float = 36f,
    val cell: Float = 16f,
    val lineThin: Float = 0.6f,
    val lineThick: Float = 1.6f,
    val numberSize: Float = 10f,
    val titleSize: Float = 14f
)

private fun writePuzzlePdf(
    outFile: File,
    box: BoxSpec,
    boards: List<BoardSpec>,
    solutionGlobal: Map<Pair<Int,Int>, Int>,
    includeSolutionPage: Boolean,
    variantTitle: String? = null
) {
    val layout = PageLayout()
    val doc = Document(layout.pageSize, layout.marginLeft, layout.marginRight, layout.marginTop, layout.marginBottom)
    val writer = PdfWriter.getInstance(doc, outFile.outputStream())
    doc.open()

    val bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED)
    val titleFont = Font(bf, layout.titleSize, Font.BOLD)
    val cb = writer.directContent

    doc.add(Paragraph("Sudoku Puzzle", titleFont))
    doc.add(Paragraph("Variant: ${variantTitle ?: variantName(boards, box)}"))

    drawBoards(cb, layout, box, boards, drawNumbersFromBoards = true, solutionGlobal = solutionGlobal)

    if (includeSolutionPage) {
        doc.newPage()
        doc.add(Paragraph("Solution", titleFont))
        drawBoards(cb, layout, box, boards, drawNumbersFromBoards = false, solutionGlobal = solutionGlobal)
    }

    doc.close()
}

private fun variantName(boards: List<BoardSpec>, box: BoxSpec): String {
    if (boards.size == 1) return "Single"

    // Identify by specific offsets (for 9x9 variants that we define)
    if (box.N == 9) {
        val offsets = boards.map { it.offsetRow to it.offsetCol }.toSet()
        val samurai = setOf(0 to 0, 0 to 12, 6 to 6, 12 to 0, 12 to 12)
        val plus4  = setOf(0 to 6, 6 to 0, 6 to 12, 12 to 6)
        if (offsets == samurai && boards.size == 5) return "Samurai"
        if (offsets == plus4  && boards.size == 4) return "Plus4"
    }

    // Detect tiled multi layout (rows x cols) from distinct offsets
    val distinctRows = boards.map { it.offsetRow }.distinct().sorted()
    val distinctCols = boards.map { it.offsetCol }.distinct().sorted()
    val r = distinctRows.size
    val c = distinctCols.size
    if (r * c == boards.size && (r > 1 || c > 1)) {
        return "Multi ${r}×${c}"
    }

    return "Multi (${boards.size} grids)"
}

private fun drawBoards(
    cb: PdfContentByte,
    layout: PageLayout,
    box: BoxSpec,
    boards: List<BoardSpec>,
    drawNumbersFromBoards: Boolean,
    solutionGlobal: Map<Pair<Int,Int>, Int>
) {
    val allRows = boards.minOf { it.offsetRow } .. boards.maxOf { it.offsetRow + box.N - 1 }
    val allCols = boards.minOf { it.offsetCol } .. boards.maxOf { it.offsetCol + box.N - 1 }
    val widthCells = allCols.last - allCols.first + 1
    val heightCells = allRows.last - allRows.first + 1

    // Auto scale cell size to fit page
    val usableW = layout.pageSize.width - layout.marginLeft - layout.marginRight
    val usableH = layout.pageSize.height - layout.marginTop - layout.marginBottom
    val cellSize = kotlin.math.min(usableW / widthCells, usableH / heightCells)

    val gridW = widthCells * cellSize
    val gridH = heightCells * cellSize
    val originX = layout.marginLeft + (usableW - gridW) / 2f
    val originY = layout.marginBottom + (usableH - gridH) / 2f

    // Gitterlinjer pr. del-bræt
    for (b in boards) {
        drawSingleGridLines(
            cb, layout, box,
            originX + (b.offsetCol - allCols.first) * cellSize,
            originY + (b.offsetRow - allRows.first) * cellSize,
            cellSize
        )
    }

    // Tal
    for (b in boards) {
        val startX = originX + (b.offsetCol - allCols.first) * cellSize
        val startY = originY + (b.offsetRow - allRows.first) * cellSize
        for (r in 0 until box.N) for (c in 0 until box.N) {
            val v = if (drawNumbersFromBoards) b.givens[r][c]
            else solutionGlobal[(b.offsetRow + r) to (b.offsetCol + c)] ?: 0
            if (v != 0) drawCenteredNumber(cb, startX, startY, layout, r, c, v, cellSize)
        }
    }
}

private fun drawSingleGridLines(cb: PdfContentByte, layout: PageLayout, box: BoxSpec, startX: Float, startY: Float, cellSize: Float) {
    val N = box.N
    cb.saveState()
    cb.setLineWidth(layout.lineThin)
    for (i in 0..N) {
        val y = startY + i * cellSize
        cb.moveTo(startX, y); cb.lineTo(startX + N * cellSize, y)
        val x = startX + i * cellSize
        cb.moveTo(x, startY); cb.lineTo(x, startY + N * cellSize)
    }
    cb.stroke()
    cb.restoreState()

    cb.saveState()
    cb.setLineWidth(layout.lineThick)
    for (br in 0..(N / box.boxRows)) {
        val y = startY + br * box.boxRows * cellSize
        cb.moveTo(startX, y); cb.lineTo(startX + N * cellSize, y)
    }
    for (bc in 0..(N / box.boxCols)) {
        val x = startX + bc * box.boxCols * cellSize
        cb.moveTo(x, startY); cb.lineTo(x, startY + N * cellSize)
    }
    cb.rectangle(startX, startY, N * cellSize, N * cellSize)
    cb.stroke()
    cb.restoreState()
}

private fun drawCenteredNumber(
    cb: PdfContentByte,
    startX: Float,
    startY: Float,
    layout: PageLayout,
    r: Int,
    c: Int,
    v: Int,
    cellSize: Float
) {
    val bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED)
    val x = startX + c * cellSize + cellSize / 2f
    val y = startY + (cellSize * (r + 1)) - cellSize * 0.72f
    cb.beginText()
    cb.setFontAndSize(bf, minOf(layout.numberSize, cellSize * 0.7f))
    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, Phrase(v.toString()), x, y, 0f)
    cb.endText()
}