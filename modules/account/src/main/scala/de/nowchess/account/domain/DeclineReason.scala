package de.nowchess.account.domain

enum DeclineReason:
  case Generic, Later, TooFast, TooSlow, TimeControl, Rated, Casual, Standard, Variant, NoBot, OnlyBot
