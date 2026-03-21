package de.nowchess.api.board

enum Color:
  case White, Black

  def opposite: Color = this match
    case White => Black
    case Black => White

  def label: String = this match
    case White => "White"
    case Black => "Black"
