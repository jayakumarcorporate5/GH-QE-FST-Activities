from datetime import datetime

# Get current year automatically
current_year = datetime.now().year

# Ask user for input
name = input("What is your name? ")
age = input("How old are you? ")

# Validate numeric age
if age.isdigit():
    age = int(age)
    year_when_100 = current_year + (100 - age)
    print(f"{name} will turn 100 years old in the year {year_when_100}.")
else:
    print("Please enter a valid numeric age.")