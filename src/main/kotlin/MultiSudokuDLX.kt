package dk.marcusrokatis

import kotlin.random.Random

/* ========================= DLX (Dancing Links) med rowId ========================= */

class DLX(private val numColumns: Int) {

    private open inner class Node {
        var L: Node = this
        var R: Node = this
        var U: Node = this
        var D: Node = this
        lateinit var C: Column
        var rowId: Int = -1
    }

    private inner class Column(val name: Int) : Node() {
        var size: Int = 0
    }

    private val header = Column(-1)
    private val columns = Array(numColumns) { Column(it) }
    private val solution = mutableListOf<Node>()
    private var solutionsFound = 0
    var solutionLimit = 1

    /** Gemmer hvilke kolonner der er 1’ere i hver tilføjet række (rowId -> IntArray af kolonneindekser) */
    private val rowColumns = mutableListOf<IntArray>()

    init {
        var prev: Node = header
        for (c in columns) {
            c.L = prev
            c.R = prev.R
            prev.R.L = c
            prev.R = c
            prev = c
        }
    }

    /** Tilføj en række; returnerer dens rowId. */
    fun addRow(cols: IntArray): Int {
        require(cols.isNotEmpty())
        val sorted = cols.distinct().sorted()
        val rowId = rowColumns.size
        rowColumns += sorted.toIntArray()

        var first: Node? = null
        var prev: Node? = null
        for (ci in sorted) {
            val col = columns[ci]
            val n = Node()
            n.C = col
            n.rowId = rowId

            // vertical insert
            n.U = col.U
            n.D = col
            col.U.D = n
            col.U = n
            col.size++

            // horizontal link
            if (first == null) first = n
            if (prev == null) {
                n.L = n; n.R = n
            } else {
                n.L = prev!!
                n.R = prev!!.R
                prev!!.R.L = n
                prev!!.R = n
            }
            prev = n
        }
        return rowId
    }

    private fun chooseColumn(): Column {
        var best: Column? = null
        var s = Int.MAX_VALUE
        var c = header.R
        while (c !== header) {
            val col = c as Column
            if (col.size < s) {
                s = col.size
                best = col
                if (s == 0) break
            }
            c = c.R
        }
        return best!!
    }

    private fun cover(c: Column) {
        c.R.L = c.L
        c.L.R = c.R
        var i = c.D
        while (i !== c) {
            var j = i.R
            while (j !== i) {
                j.D.U = j.U
                j.U.D = j.D
                j.C.size--
                j = j.R
            }
            i = i.D
        }
    }

    private fun uncover(c: Column) {
        var i = c.U
        while (i !== c) {
            var j = i.L
            while (j !== i) {
                j.C.size++
                j.D.U = j
                j.U.D = j
                j = j.L
            }
            i = i.U
        }
        c.R.L = c
        c.L.R = c
    }

    /** Callback får de valgte rowIds for en komplet exact cover. */
    fun search(onSolutionRowIds: (List<Int>) -> Unit) {
        if (header.R === header) {
            onSolutionRowIds(solution.map { it.rowId })
            solutionsFound++
            return
        }
        if (solutionsFound >= solutionLimit) return

        val c = chooseColumn()
        if (c.size == 0) return
        cover(c)

        var r = c.D
        while (r !== c) {
            solution.add(r)
            var j = r.R
            while (j !== r) {
                cover(j.C)
                j = j.R
            }

            search(onSolutionRowIds)
            if (solutionsFound >= solutionLimit) {
                var k = r.L
                while (k !== r) { uncover(k.C); k = k.L }
                solution.removeAt(solution.size - 1)
                uncover(c)
                return
            }

            var k = r.L
            while (k !== r) { uncover(k.C); k = k.L }
            solution.removeAt(solution.size - 1)
            r = r.D
        }
        uncover(c)
    }

    fun getRowColumns(rowId: Int): IntArray = rowColumns[rowId]
}

/* ========================= Generisk multi-grid Sudoku (Exact Cover) ========================= */

data class BoxSpec(val boxRows: Int, val boxCols: Int) { val N = boxRows * boxCols }

data class BoardSpec(
    val offsetRow: Int,
    val offsetCol: Int,
    /** givens[r][c] ∈ 0..N, 0 = tom */
    val givens: Array<IntArray>
)

class MultiSudokuDLX(
    private val box: BoxSpec,
    private val boards: List<BoardSpec>,
    private val rng: Random? = null // hvis sat, bruges til at randomisere rækkefølge af kandidat-rækker
) {
    private val N = box.N

    /** Global celle-id i det samlede koordinatsystem */
    private data class Cell(val gr: Int, val gc: Int)

    // cell -> (boardIndex, rLocal, cLocal)*
    private val cellOwners: Map<Cell, List<Triple<Int, Int, Int>>>
    private val globalCells: List<Cell>
    private val cellIndex: Map<Cell, Int>

    // Kolonneopdeling:
    // [A] Global cell (|C| stk)
    // [B] For hvert board: Row-Value (N*N)
    // [C] For hvert board: Col-Value (N*N)
    // [D] For hvert board: Box-Value (N*N)
    private val numCellCols: Int
    private val rowValBase: Int
    private val colValBase: Int
    private val boxValBase: Int
    private val totalCols: Int

    init {
        // Build ownership map
        val owners = mutableMapOf<Cell, MutableList<Triple<Int, Int, Int>>>()
        for ((bi, b) in boards.withIndex()) {
            require(b.givens.size == N && b.givens.all { it.size == N }) { "Board $bi givens must be N×N" }
            for (r in 0 until N) for (c in 0 until N) {
                val cell = Cell(b.offsetRow + r, b.offsetCol + c)
                owners.getOrPut(cell) { mutableListOf() }.add(Triple(bi, r, c))
            }
        }
        cellOwners = owners
        globalCells = cellOwners.keys.sortedWith(compareBy<Cell> { it.gr }.thenBy { it.gc })
        cellIndex = globalCells.mapIndexed { idx, cell -> cell to idx }.toMap()

        numCellCols = globalCells.size
        rowValBase = numCellCols
        colValBase = rowValBase + boards.size * N * N
        boxValBase = colValBase + boards.size * N * N
        totalCols   = boxValBase + boards.size * N * N
    }

    private fun rowValCol(bi: Int, r: Int, v: Int): Int = rowValBase + bi * N * N + r * N + (v - 1)
    private fun colValCol(bi: Int, c: Int, v: Int): Int = colValBase + bi * N * N + c * N + (v - 1)
    private fun boxIndex(r: Int, c: Int): Int = (r / box.boxRows) * box.boxCols + (c / box.boxCols)
    private fun boxValCol(bi: Int, b: Int, v: Int): Int = boxValBase + bi * N * N + b * N + (v - 1)

    /** Byg DLX-matrix. Kandidat-rækker oprettes pr. (globalCell, værdi v) der ikke konflikter med givens. */
    private fun buildMatrix(dlx: DLX) {
        val vOrder = (1..N).toMutableList()
        for ((cell, owners) in cellOwners) {
            val ci = cellIndex.getValue(cell)
            if (rng != null) vOrder.shuffle(rng)
            for (v in vOrder) {
                // givens-filter på alle del-brætter:
                var ok = true
                owners@ for ((bi, r, c) in owners) {
                    val g = boards[bi].givens[r][c]
                    if (g != 0 && g != v) { ok = false; break@owners }
                }
                if (!ok) continue

                val cols = ArrayList<Int>(1 + owners.size * 3)
                cols += ci // global cell skal have en værdi
                for ((bi, r, c) in owners) {
                    val b = boxIndex(r, c)
                    cols += rowValCol(bi, r, v)
                    cols += colValCol(bi, c, v)
                    cols += boxValCol(bi, b, v)
                }
                dlx.addRow(cols.toIntArray())
            }
        }
    }

    /** Løs én løsning og returnér globalt grid som map: (row, col) -> v. */
    fun solveOneGrid(): Map<Pair<Int, Int>, Int>? {
        val dlx = DLX(totalCols)
        buildMatrix(dlx)
        var result: Map<Pair<Int, Int>, Int>? = null

        dlx.solutionLimit = 1
        dlx.search { chosenRowIds ->
            val grid = mutableMapOf<Pair<Int, Int>, Int>()
            for (rowId in chosenRowIds) {
                val cols = dlx.getRowColumns(rowId)
                // Find global cell-kolonnen (0 .. numCellCols-1)
                var globalColIdx = -1
                var v = -1
                for (ci in cols) {
                    when (ci) {
                        in 0 until numCellCols -> globalColIdx = ci
                        in rowValBase until (rowValBase + boards.size * N * N) -> {
                            // rowVal: idx = ci - rowValBase; v = (idx % N) + 1
                            val idx = ci - rowValBase
                            v = (idx % N) + 1
                        }
                        in colValBase until (colValBase + boards.size * N * N) -> {
                            val idx = ci - colValBase
                            v = (idx % N) + 1
                        }
                        in boxValBase until (boxValBase + boards.size * N * N) -> {
                            val idx = ci - boxValBase
                            v = (idx % N) + 1
                        }
                    }
                }
                require(globalColIdx >= 0 && v >= 1) { "Dekodning fejlede for en valgt række" }
                val cell = globalCells[globalColIdx]
                grid[cell.gr to cell.gc] = v
            }
            result = grid
        }
        return result
    }

    /** Tæl løsninger op til limit (brug fx 2 for unikhedstjek). */
    fun countSolutions(limit: Int = 2): Int {
        val dlx = DLX(totalCols)
        buildMatrix(dlx)
        var count = 0
        dlx.solutionLimit = limit
        dlx.search { count++ }
        return count
    }

    /** Tæl globale givens (samler overlappende celler så de kun tælles én gang). */
    fun countGlobalGivens(): Int {
        var cnt = 0
        for ((cell, owners) in cellOwners) {
            // tjek om cellen er given i alle del-brætter der indeholder den (samme værdi)
            var v = 0
            var consistent = true
            for ((bi, r, c) in owners) {
                val g = boards[bi].givens[r][c]
                if (g == 0) { consistent = false; break }
                if (v == 0) v = g else if (v != g) { consistent = false; break }
            }
            if (consistent && v != 0) cnt++
        }
        return cnt
    }

    /** Sæt (row, col) globalt til 0 (fjern given) i alle boards der ejer cellen. */
    fun clearGlobalCell(gr: Int, gc: Int) {
        val owners = cellOwners[Cell(gr, gc)] ?: return
        for ((bi, r, c) in owners) {
            boards[bi].givens[r][c] = 0
        }
    }

    /** Sæt (row, col) globalt til v i alle boards der ejer cellen. */
    fun setGlobalCell(gr: Int, gc: Int, v: Int) {
        val owners = cellOwners[Cell(gr, gc)] ?: return
        for ((bi, r, c) in owners) {
            boards[bi].givens[r][c] = v
        }
    }

    /** Returnér liste over alle globale celler (koordinater). */
    fun allGlobalCells(): List<Pair<Int, Int>> = globalCells.map { it.gr to it.gc }
}

/* ========================= Layouts (9×9) ========================= */

/** Samurai (5×9×9) i 21×21-koordinater:
 * TL(0,0), TR(0,12), C(6,6), BL(12,0), BR(12,12)
 */
fun samuraiLayout(
    tl: Array<IntArray>, tr: Array<IntArray>, c: Array<IntArray>, bl: Array<IntArray>, br: Array<IntArray>
): Pair<BoxSpec, List<BoardSpec>> {
    val box = BoxSpec(3,3)
    return box to listOf(
        BoardSpec(0, 0, tl),
        BoardSpec(0, 12, tr),
        BoardSpec(6, 6, c),
        BoardSpec(12, 0, bl),
        BoardSpec(12, 12, br)
    )
}

/** Plus4: fire 9×9 der overlapper i “plus”-mønster (uden centerbræt):
 * U(0,6), L(6,0), R(6,12), D(12,6)
 */
fun plus4Layout(
    up: Array<IntArray>, left: Array<IntArray>, right: Array<IntArray>, down: Array<IntArray>
): Pair<BoxSpec, List<BoardSpec>> {
    val box = BoxSpec(3,3)
    return box to listOf(
        BoardSpec(0, 6, up),
        BoardSpec(6, 0, left),
        BoardSpec(6, 12, right),
        BoardSpec(12, 6, down)
    )
}

/** Hjælpere til tomme givens */
fun emptyNxN(n: Int): Array<IntArray> = Array(n) { IntArray(n) }
fun empty9x9(): Array<IntArray> = emptyNxN(9)
fun empty4x4(): Array<IntArray> = emptyNxN(4)

/* ========================= Generator til single/multi-layout ========================= */

data class GeneratedPuzzle(
    val box: BoxSpec,
    val boards: List<BoardSpec>,           // givens (opgaven)
    val solutionGlobal: Map<Pair<Int,Int>, Int> // fuld global løsning
)

object MultiSudokuGenerator {

    /** Generér fuld løsning (global) fra layout ved at bruge DLX uden givens, evt. randomiseret af seed. */
    private fun fullSolution(
        box: BoxSpec,
        boards: List<BoardSpec>,
        seed: Long
    ): Map<Pair<Int,Int>, Int> {
        // Clone tomme givens
        val blankBoards = boards.map { b ->
            val g = Array(box.N) { IntArray(box.N) }
            BoardSpec(b.offsetRow, b.offsetCol, g)
        }
        val solver = MultiSudokuDLX(box, blankBoards, rng = Random(seed))
        return solver.solveOneGrid()
            ?: error("Kunne ikke finde fuld løsning (bør ikke ske for standard layouts).")
    }

    /** Udfyld alle givens i boards med den globale løsning. */
    private fun fillBoardsWithSolution(
        box: BoxSpec,
        boards: List<BoardSpec>,
        solutionGlobal: Map<Pair<Int,Int>, Int>
    ) {
        // Nulstil og udfyld
        for (b in boards) for (r in 0 until box.N) for (c in 0 until box.N) b.givens[r][c] = 0
        // Sæt alle globalt
        val temp = MultiSudokuDLX(box, boards)
        for ((pos, v) in solutionGlobal) {
            val (gr, gc) = pos
            temp.setGlobalCell(gr, gc, v)
        }
    }

    /** Carving: fjern globalt en celle ad gangen mens unikhed bevares (DLX tæller op til 2). */
    private fun carve(
        box: BoxSpec,
        boards: List<BoardSpec>,
        rng: Random,
        minGlobalGivens: Int,
        maxAttempts: Int = 100_000
    ) {
        val solver = MultiSudokuDLX(box, boards)
        val cells = solver.allGlobalCells().toMutableList()
        cells.shuffle(rng)

        var attempts = 0
        while (cells.isNotEmpty() && attempts < maxAttempts && solver.countGlobalGivens() > minGlobalGivens) {
            attempts++
            val (gr, gc) = cells.removeLast()

            // Gem værdi for evt. rollback
            val beforeVal = (solver.solveOneGrid() ?: break)[gr to gc] // fra løsningen kan vi slå op
            // (alternativt kunne vi lægge solutionGlobal ind som parameter for hurtigere opslag)

            // Fjern
            solver.clearGlobalCell(gr, gc)

            // Unikhed?
            val unique = solver.countSolutions(limit = 2) == 1
            if (!unique) {
                // rollback
                solver.setGlobalCell(gr, gc, beforeVal ?: 0)
            }
        }
    }

    /** API: Generér puzzle for et givent layout (single, Samurai, Plus4, …) */
    fun generate(
        box: BoxSpec,
        boards: List<BoardSpec>,
        seed: Long = System.nanoTime(),
        minGlobalGivens: Int = 26,  // for multi-layouts giver større total god mening; hæv dette tal
        symmetryHint: Boolean = false // (valgfri) – global symmetri er sværere med overlappende grids; ikke brugt her
    ): GeneratedPuzzle {
        val rng = Random(seed)

        // 1) find fuld global løsning (randomiseret af seed)
        val solutionGlobal = fullSolution(box, boards, seed)

        // 2) fyld alle givens = fuld synlig løsning (udgangspunkt for carving)
        fillBoardsWithSolution(box, boards, solutionGlobal)

        // 3) fjern givens globalt med DLX-unikhed
        carve(box, boards, rng, minGlobalGivens)

        return GeneratedPuzzle(box, boards, solutionGlobal)
    }
}

/* ========================= Pretty-print helpers (valgfrit) ========================= */

fun Map<Pair<Int,Int>,Int>.renderGlobal(minRow: Int, maxRow: Int, minCol: Int, maxCol: Int): String = buildString {
    for (r in minRow..maxRow) {
        for (c in minCol..maxCol) {
            val v = this@renderGlobal[r to c]
            append(if (v == null) ". " else "$v ")
        }
        appendLine()
    }
}

/* ========================= Demo ========================= */

fun main() {
    // ---- 1) Enkelt 4×4 (Snackdoku) – demonstrér solveOneGrid() ----
    run {
        val box = BoxSpec(2,2) // N=4
        val givens = empty4x4()
        val (bx, boards) = Pair(box, listOf(BoardSpec(0,0,givens)))

        // Sæt et par givens for demo
        givens[0][1] = 3
        givens[1][0] = 4

        val solver = MultiSudokuDLX(bx, boards, rng = Random(1234))
        val grid = solver.solveOneGrid()
        println("4×4 løsning (globalt map): ${grid?.size} felter")
        println(grid?.renderGlobal(0,3,0,3))
    }

    // ---- 2) Samurai (5×9×9) – generér puzzle med DLX-unikhed ----
    run {
        val tl = empty9x9(); val tr = empty9x9(); val cc = empty9x9(); val bl = empty9x9(); val br = empty9x9()
        val (box, boards) = samuraiLayout(tl, tr, cc, bl, br)

        val result = MultiSudokuGenerator.generate(
            box, boards,
            seed = 42L,
            minGlobalGivens = 160 // juster efter ønsket tomhed/sværhedsgrad (samurai har mange globale celler)
        )

        val solver = MultiSudokuDLX(result.box, result.boards)
        val uniqueness = solver.countSolutions(2)
        println("Samurai: unikhedstjek = $uniqueness (1 betyder unik)")

        // Render et udsnit af det globale område (0..20 for både r og c)
        val solved = result.solutionGlobal
        println("Samurai – udsnit af løsning 0..20:")
        println(solved.renderGlobal(0,20,0,20))
    }

    // ---- 3) Plus4 (4×9×9 i plus-struktur) – generér puzzle ----
    run {
        val up = empty9x9(); val left = empty9x9(); val right = empty9x9(); val down = empty9x9()
        val (box, boards) = plus4Layout(up, left, right, down)

        val result = MultiSudokuGenerator.generate(
            box, boards,
            seed = 1337L,
            minGlobalGivens = 140
        )

        val solver = MultiSudokuDLX(result.box, result.boards)
        val uniqueness = solver.countSolutions(2)
        println("Plus4: unikhedstjek = $uniqueness (1 betyder unik)")
    }
}