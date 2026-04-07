print("Rock-Paper-Scissors Game!")

# Get choices from both players
player1 = input("Player 1, enter rock, paper, or scissors: ").lower()
player2 = input("Player 2, enter rock, paper, or scissors: ").lower()

# Check for tie
if player1 == player2:
    print("It's a tie!")

# Player 1 winning conditions
elif (player1 == "rock" and player2 == "scissors") or \
     (player1 == "scissors" and player2 == "paper") or \
     (player1 == "paper" and player2 == "rock"):
    print("Player 1 wins!")

# Otherwise Player 2 wins
else:
    print("Player 2 wins!")