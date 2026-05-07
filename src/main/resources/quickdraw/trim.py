import os

folder = "."
for filename in os.listdir(folder):
    if filename.endswith(".ndjson"):
        filepath = os.path.join(folder, filename)
        with open(filepath, "r", encoding="utf-8") as f:
            lines = [f.readline() for _ in range(30)]
        with open(filepath, "w", encoding="utf-8") as f:
            f.writelines(lines)

print("완료")