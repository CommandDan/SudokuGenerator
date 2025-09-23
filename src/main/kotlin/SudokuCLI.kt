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
    private val mode by option("--mode", "-m", help = "single | samurai | plus4")
        .choice("single", "samurai", "plus4", "multi")
        .default("multi")

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
    val solutionGlobal: Map<Pair<Int,Int>, Int>,      // samlet løsning mappet til globale (row,col)
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
    includeSolutionPage: Boolean
) {
    val layout = PageLayout()
    val doc = Document(layout.pageSize, layout.marginLeft, layout.marginRight, layout.marginTop, layout.marginBottom)
    val writer = PdfWriter.getInstance(doc, outFile.outputStream())
    doc.open()

    val bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED)
    val titleFont = Font(bf, layout.titleSize, Font.BOLD)
    val cb = writer.directContent

    doc.add(Paragraph("Sudoku Puzzle", titleFont))
    doc.add(Paragraph("Variant: ${variantName(boards)}"))

    drawBoards(cb, layout, box, boards, drawNumbersFromBoards = true, solutionGlobal = solutionGlobal)

    if (includeSolutionPage) {
        doc.newPage()
        doc.add(Paragraph("Solution", titleFont))
        drawBoards(cb, layout, box, boards, drawNumbersFromBoards = false, solutionGlobal = solutionGlobal)
    }

    doc.close()
}

private fun variantName(boards: List<BoardSpec>) = when (boards.size) {
    1 -> "Single"
    4 -> "Plus4"
    5 -> "Samurai"
    else -> "Multi (${boards.size} grids)"
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

    val gridW = widthCells * layout.cell
    val gridH = heightCells * layout.cell
    val usableW = layout.pageSize.width - layout.marginLeft - layout.marginRight
    val usableH = layout.pageSize.height - layout.marginTop - layout.marginBottom
    val originX = layout.marginLeft + (usableW - gridW) / 2f
    val originY = layout.marginBottom + (usableH - gridH) / 2f

    // Gitterlinjer pr. del-bræt
    for (b in boards) {
        drawSingleGridLines(
            cb, layout, box,
            originX + (b.offsetCol - allCols.first) * layout.cell,
            originY + (b.offsetRow - allRows.first) * layout.cell
        )
    }

    // Tal
    for (b in boards) {
        val startX = originX + (b.offsetCol - allCols.first) * layout.cell
        val startY = originY + (b.offsetRow - allRows.first) * layout.cell
        for (r in 0 until box.N) for (c in 0 until box.N) {
            val v = if (drawNumbersFromBoards) b.givens[r][c]
            else solutionGlobal[(b.offsetRow + r) to (b.offsetCol + c)] ?: 0
            if (v != 0) drawCenteredNumber(cb, startX, startY, layout, r, c, v)
        }
    }
}

private fun drawSingleGridLines(cb: PdfContentByte, layout: PageLayout, box: BoxSpec, startX: Float, startY: Float) {
    val N = box.N
    cb.saveState()
    cb.setLineWidth(layout.lineThin)
    for (i in 0..N) {
        val y = startY + i * layout.cell
        cb.moveTo(startX, y); cb.lineTo(startX + N * layout.cell, y)
        val x = startX + i * layout.cell
        cb.moveTo(x, startY); cb.lineTo(x, startY + N * layout.cell)
    }
    cb.stroke()
    cb.restoreState()

    cb.saveState()
    cb.setLineWidth(layout.lineThick)
    for (br in 0..(N / box.boxRows)) {
        val y = startY + br * box.boxRows * layout.cell
        cb.moveTo(startX, y); cb.lineTo(startX + N * layout.cell, y)
    }
    for (bc in 0..(N / box.boxCols)) {
        val x = startX + bc * box.boxCols * layout.cell
        cb.moveTo(x, startY); cb.lineTo(x, startY + N * layout.cell)
    }
    cb.rectangle(startX, startY, N * layout.cell, N * layout.cell)
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
    v: Int
) {
    val bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED)
    val x = startX + c * layout.cell + layout.cell / 2f
    val y = startY + (layout.cell * (r + 1)) - layout.cell * 0.72f
    cb.beginText()
    cb.setFontAndSize(bf, layout.numberSize)
    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, Phrase(v.toString()), x, y, 0f)
    cb.endText()
}