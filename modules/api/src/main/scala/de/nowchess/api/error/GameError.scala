package de.nowchess.api.error

enum GameError:
  case ParseError(details: String)
  case FileReadError(details: String)
  case FileWriteError(details: String)
  case IllegalMove

  def message: String = this match
    case ParseError(d)     => d
    case FileReadError(d)  => d
    case FileWriteError(d) => d
    case IllegalMove       => "Illegal move"
