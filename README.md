# Jackaroo

A JavaFX version of Jackaroo, the marbles-and-cards board game, with a Balatro-inspired pixel UI.
You play against three CPU opponents: race all four of your marbles around the track and into your safe zone first.

## Running

Needs a JDK 17+ that includes JavaFX (for example [Azul Zulu "JDK FX"](https://www.azul.com/downloads/?package=jdk-fx)).

- **Windows:** double-click `run.bat`. It compiles everything into `out/` and starts the game.
  If a JDK is unpacked under `.tools/zulu*` it uses that, otherwise `javac`/`java` from your PATH.
- **Maven:** `mvn javafx:run`

Run from the project folder, since the game reads `Cards.csv` from the working directory.

## Project layout

- `src/engine`, `src/model`, `src/exception`: the game logic (board, cards, players, CPU)
- `src/application`: the JavaFX UI
- `src/main/resources/fonts`: the Pixelify Sans pixel font (SIL Open Font License, see `OFL.txt`)
