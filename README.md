# SudokuGenerator

Et Kotlin-baseret værktøj til at generere **sudoku-opgaver** som PDF-filer.  
Projektet understøtter både klassiske **single**-sudokuer samt varianterne **samurai**, **plus4** og **multi** (flere single-sudokuer på samme ark).

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
java -jar build/libs/SudokuGenerator-all.jar --mode single --out sudoku.pdf
```

Hvis du bruger distributions-zippen fra GitHub Releases, kan du starte via:

- **Unix/macOS**:
  ```bash
  bin/sudoku-generator-shadow --mode single --out sudoku.pdf
  ```

- **Windows**:
  ```bat
  bin\sudoku-generator-shadow.bat --mode samurai --out samurai.pdf
  ```

---

## ⚙️ Parametre

| Flag              | Beskrivelse                                    | Default             |
|-------------------|------------------------------------------------|---------------------|
| `--mode`          | Variant: `single`, `samurai`, `plus4`, `multi` | `multi`             |
| `--out`           | Output PDF-fil                                 | `sudoku.pdf`        |
| `--seed`          | RNG seed (heltal)                              | `System.nanoTime()` |
| `--min-givens`    | Minimum antal givne tal i opgaven              | `26`                |
| `--solution-page` | Tilføj ekstra side i PDF’en med løsningen      | `false`             |
| `--n`             | Brætstørrelse (fx `9` for 9×9, `4` for 4×4)    | `9`                 |
| `--box-rows`      | Antal rækker i hver boks                       | `3`                 |
| `--box-cols`      | Antal kolonner i hver boks                     | `3`                 |
| **Kun for multi** |                                                |                     |
| `--rows`          | Antal rækker af brætter                        | `3`                 |
| `--cols`          | Antal kolonner af brætter                      | `2`                 |
| `--gap-cells`     | Tomt mellemrum (i celler) mellem brætter       | `3`                 |
| `--vary-seeds`    | Brug forskelligt seed for hvert bræt           | `true`              |

---

## 🖼️ Output

- **Single**: ét klassisk sudoku-bræt
- **Samurai**: fem overlappende 9×9-brætter
- **Plus4**: fire 9×9-brætter i krydsstruktur
- **Multi**: flere uafhængige sudoku-brætter placeret på samme side

PDF’en kan også indeholde en ekstra side med løsningen, hvis du angiver `--solution-page`.

---

## 📜 Licens

Dette projekt bruger [OpenPDF](https://github.com/LibrePDF/OpenPDF) (LGPL/MPL) og [Clikt](https://ajalt.github.io/clikt/) til CLI.  
Selve generatoren er MIT-licenseret (medmindre du ændrer det).
