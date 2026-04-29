package de.nowchess.account.error

enum AccountError:
  case UsernameTaken(username: String)
  case EmailAlreadyRegistered(email: String)
  case InvalidCredentials
  case UserNotFound
  case BotNotFound
  case BotLimitExceeded
  case NotAuthorized
  case UserBanned
  case BotBanned

  def message: String = this match
    case UsernameTaken(u)          => s"Username '$u' is already taken"
    case EmailAlreadyRegistered(e) => s"Email '$e' is already registered"
    case InvalidCredentials        => "Invalid credentials"
    case UserNotFound              => "User not found"
    case BotNotFound               => "Bot account not found"
    case BotLimitExceeded          => "Maximum of 5 bot accounts per user exceeded"
    case NotAuthorized             => "Not authorized to perform this action"
    case UserBanned                => "User account is banned"
    case BotBanned                 => "Bot account is banned"
