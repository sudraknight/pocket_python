# Your pocket-sized Python workspace.
# Tap the green play button to run this file.

from math import sqrt


def greet(name):
    return f"Hello, {name}!"


print(greet("world"))

squares = [n ** 2 for n in range(1, 6)]
print("Squares:", squares)
print("Square root of 144:", sqrt(144))

# Interactive input works in the terminal below:
# name = input("What is your name? ")
# print(greet(name))

# Also included: numpy, requests, sympy and PIL.
