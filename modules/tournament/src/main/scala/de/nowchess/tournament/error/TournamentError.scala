package de.nowchess.tournament.error

enum TournamentError(val message: String):
  case NotFound(id: String)          extends TournamentError(s"Tournament $id not found")
  case NotDirector                   extends TournamentError("Not the tournament director")
  case WrongStatus(expected: String) extends TournamentError(s"Tournament must be in $expected status")
  case AlreadyJoined                 extends TournamentError("Already joined this tournament")
  case NotJoined                     extends TournamentError("Not joined this tournament")
  case NotEnoughParticipants         extends TournamentError("Need at least 2 participants to start")
  case NotABot                       extends TournamentError("Only bot accounts can join tournaments")
