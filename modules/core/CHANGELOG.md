##  (2026-03-27)

### Features

* add GameRules stub with PositionStatus enum ([76d4168](https://git.janis-eccarius.de/NowChess/NowChessSystems/commit/76d4168038de23e5d6083d4e8f0504fbf31d15a3))
* add MovedInCheck/Checkmate/Stalemate MoveResult variants (stub dispatch) ([8b7ec57](https://git.janis-eccarius.de/NowChess/NowChessSystems/commit/8b7ec57e5ea6ee1615a1883848a426dc07d26364))
* implement GameRules with isInCheck, legalMoves, gameStatus ([94a02ff](https://git.janis-eccarius.de/NowChess/NowChessSystems/commit/94a02ff6849436d9496c70a0f16c21666dae8e4e))
* implement legal castling ([#1](https://git.janis-eccarius.de/NowChess/NowChessSystems/issues/1)) ([00d326c](https://git.janis-eccarius.de/NowChess/NowChessSystems/commit/00d326c1ba67711fbe180f04e1100c3f01dd0254))
* wire check/checkmate/stalemate into processMove and gameLoop ([5264a22](https://git.janis-eccarius.de/NowChess/NowChessSystems/commit/5264a225418b885c5e6ea6411b96f85e38837f6c))

### Bug Fixes

* add missing kings to gameLoop capture test board ([aedd787](https://git.janis-eccarius.de/NowChess/NowChessSystems/commit/aedd787b77203c2af934751dba7b784eaf165032))
* correct test board positions and captureOutput/withInput interaction ([f0481e2](https://git.janis-eccarius.de/NowChess/NowChessSystems/commit/f0481e2561b779df00925b46ee281dc36a795150))
* update main class path in build configuration and adjust VCS directory mapping ([7b1f8b1](https://git.janis-eccarius.de/NowChess/NowChessSystems/commit/7b1f8b117623d327232a1a92a8a44d18582e0189))
