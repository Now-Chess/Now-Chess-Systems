package de.nowchess.api.game

enum TimeControl:
  case Clock(limitSeconds: Int, incrementSeconds: Int)
  case Correspondence(daysPerMove: Int)
  case Unlimited
