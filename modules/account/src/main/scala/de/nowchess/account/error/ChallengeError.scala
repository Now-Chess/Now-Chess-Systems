package de.nowchess.account.error

enum ChallengeError:
  case UserNotFound(username: String)
  case ChallengerNotFound
  case CannotChallengeSelf
  case DuplicateChallenge
  case InvalidColor(color: String)
  case InvalidDeclineReason(reason: String)
  case ChallengeNotFound
  case ChallengeNotActive
  case NotAuthorized
  case GameCreationFailed

  def message: String = this match
    case UserNotFound(u)         => s"User '$u' not found"
    case ChallengerNotFound      => "Challenger not found"
    case CannotChallengeSelf     => "Cannot challenge yourself"
    case DuplicateChallenge      => "Active challenge to this user already exists"
    case InvalidColor(c)         => s"Unknown color: $c"
    case InvalidDeclineReason(r) => s"Unknown decline reason: $r"
    case ChallengeNotFound       => "Challenge not found"
    case ChallengeNotActive      => "Challenge is not active"
    case NotAuthorized           => "Not authorized"
    case GameCreationFailed      => "Failed to create game"
