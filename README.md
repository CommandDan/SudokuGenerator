# SudokuGenerator

Et Kotlin-baseret værktøj til at generere **sudoku-opgaver** som PDF-filer.  
Understøtter **single**, **samurai**, **plus4**, **multi** (flere single pr. side) samt **dual**-varianter (to samurai/plus4 på samme side).  
Kør i **portrait** som standard eller brug **--landscape** for bredformat.

---

## 🛠️ Bygning

Projektet bruger **Gradle** og kræver en JDK (Java 17 eller nyere anbefales).

Byg hele projektet:

```bash
./gradlew build
```

For at bygge en **fat jar** med alle afhængigheder:

```bash
./gradlew shadowJar
```

Artefakter findes herefter i `build/libs/`.

For en release med scripts (.sh/.bat) kan du køre:

```bash
./gradlew releaseShadowDistZip
```

---

## ▶️ Kørsel

Programmet kan køres direkte via Gradle:

```bash
./gradlew run --args="--mode single --out sudoku.pdf"
```

Eller med JAR-filen:

```bash
java -jar build/libs/SudokuGenerator-<version>-all.jar --mode single --out sudoku.pdf
```

Hvis du bruger distributions-zippen fra GitHub Releases, kan du starte via:

- **Unix/macOS**:
  ```bash
  bin/SudokuGenerator --mode single --out sudoku.pdf
  ```

- **Windows**:
  ```bat
  bin\SudokuGenerator.bat --mode samurai --out samurai.pdf
  ```

Eksempler:

```bash
# To Samurai på samme side (vertikalt; brug --landscape for side-by-side)
bin/SudokuGenerator --mode samurai-dual --out samurai-dual.pdf

# Multi i landscape – bytter automatisk rows/cols for at udnytte siden
bin/SudokuGenerator --mode multi --rows 2 --cols 3 --landscape --out multi-2x3.pdf
```

---

## ⚙️ Parametre

| Flag                | Beskrivelse                                                                  | Default              |
|---------------------|------------------------------------------------------------------------------|----------------------|
| `--mode`            | `single`, `samurai`, `plus4`, `multi`, `samurai-dual`, `plus4-dual`          | `multi`              |
| `--out`             | Output PDF-fil                                                               | `sudoku.pdf`         |
| `--seed`            | RNG seed (heltal)                                                            | `System.nanoTime()`  |
| `--solution-page`   | Tilføj ekstra side i PDF’en med løsningen                                    | `false`              |
| `--landscape`       | Brug A4 i bredformat. Dual modes: side-by-side i landscape, vertikalt ellers | `false`              |
| `--min-givens`      | Overstyrer min. antal givne tal (ellers vælges automatisk)                   | *auto*               |
| `--difficulty`      | Sværhedsgrad som heltal `1 ..< N` (1 = nemmest, større = sværere)            | *afledt af level*    |
| `--level`           | Niveaunavn: `easy`, `medium`, `hard`, `expert`                               | `medium`             |
| `--n`               | Brætstørrelse (fx `9` for 9×9, `4` for 4×4)                                  | `9`                  |
| `--box-rows`        | Antal rækker i hver boks                                                     | `3`                  |
| `--box-cols`        | Antal kolonner i hver boks                                                   | `3`                  |
| **Kun for multi**   |                                                                              |                      |
| `--rows`            | Antal rækker af brætter                                                      | `3`                  |
| `--cols`            | Antal kolonner af brætter                                                    | `2`                  |
| `--gap-cells`       | Tomt mellemrum (i celler) mellem brætter                                     | `3`                  |
| `--vary-seeds`      | Brug forskelligt seed for hvert bræt                                         | `true`               |

### Sværhedsgrad & automatisk `min-givens`
Hvis `--min-givens` **ikke** angives, vælges antallet automatisk ud fra `--difficulty` / `--level` og brætstørrelsen `N`.
Modellen normaliserer sværhedsgraden og interpolerer mellem følgende **clue-ratioer** (andel givne felter):

- **4×4**: easy ≈ **0.70** → expert ≈ **0.50**
- **9×9**: easy ≈ **0.52** → expert ≈ **0.26**
- **16×16**: easy ≈ **0.42** → expert ≈ **0.24**

Antallet beregnes på tværs af **unikke celler** i layoutet (overlap tælles kun én gang), så fx Samurai/Plus4/dual/multi får en realistisk samlet mængde givens.  
Brug `--min-givens` for at overstyre automatisk valg.

---

## 🖼️ Output

- **Single**: ét klassisk sudoku-bræt
- **Samurai**: fem overlappende 9×9-brætter
- **Plus4**: fire 9×9-brætter i krydsstruktur
- **Multi**: flere uafhængige sudoku-brætter placeret på samme side (R×C)
- **Dual**: to *samurai* eller to *plus4* på samme side (vertikalt; **side-by-side** med `--landscape`)

PDF’en kan også indeholde en ekstra side med løsningen, hvis du angiver `--solution-page`.

---

## 📜 Licens

Dette projekt bruger [OpenPDF](https://github.com/LibrePDF/OpenPDF) (LGPL/MPL) og [Clikt](https://ajalt.github.io/clikt/) til CLI.  
Selve generatoren er MIT-licenseret (medmindre du ændrer det).
