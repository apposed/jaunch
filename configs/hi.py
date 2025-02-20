import sys

if len(sys.argv) > 1:
    print(f"Hello, {' '.join(sys.argv[1:])}!")
else:
    print("Hello from Python!")
