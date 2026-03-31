package de.nowchess.chess.command

/** Manages command execution and history for undo/redo support. */
class CommandInvoker:
  private val executedCommands = scala.collection.mutable.ListBuffer[Command]()
  private var currentIndex = -1

  /** Execute a command and add it to history.
   *  Discards any redo history if not at the end of the stack.
   */
  def execute(command: Command): Boolean = synchronized {
    if command.execute() then
      // Remove any commands after current index (redo stack is discarded)
      while currentIndex < executedCommands.size - 1 do
        executedCommands.remove(executedCommands.size - 1)
      executedCommands += command
      currentIndex += 1
      true
    else
      false
  }

  /** Undo the last executed command if possible. */
  def undo(): Boolean = synchronized {
    if currentIndex >= 0 && currentIndex < executedCommands.size then
      val command = executedCommands(currentIndex)
      if command.undo() then
        currentIndex -= 1
        true
      else
        false
    else
      false
  }

  /** Redo the next command in history if available. */
  def redo(): Boolean = synchronized {
    if currentIndex + 1 < executedCommands.size then
      val command = executedCommands(currentIndex + 1)
      if command.execute() then
        currentIndex += 1
        true
      else
        false
    else
      false
  }

  /** Get the history of all executed commands. */
  def history: List[Command] = synchronized {
    executedCommands.toList
  }

  /** Get the current position in command history. */
  def getCurrentIndex: Int = synchronized {
    currentIndex
  }

  /** Clear all command history. */
  def clear(): Unit = synchronized {
    executedCommands.clear()
    currentIndex = -1
  }

  /** Check if undo is available. */
  def canUndo: Boolean = synchronized {
    currentIndex >= 0
  }

  /** Check if redo is available. */
  def canRedo: Boolean = synchronized {
    currentIndex + 1 < executedCommands.size
  }
