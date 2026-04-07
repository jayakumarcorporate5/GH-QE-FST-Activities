import pandas as pd

# Sample data to write into CSV
data = {
    "ID": [1, 2, 3, 4, 5],
    "Name": ["Arun", "Meera", "Raj", "Sneha", "David"],
    "Department": ["Engineering", "Finance", "Marketing", "Engineering", "HR"],
    "Location": ["Bangalore", "Delhi", "Mumbai", "Chennai", "Bangalore"],
    "Score": [87, 92, 74, 81, 69],
    "Status": ["Active", "Active", "Inactive", "Active", "Active"],
    "Date": ["2026-01-10", "2026-01-12", "2026-01-15", "2026-01-18", "2026-01-21"]
}

# Create DataFrame
df = pd.DataFrame(data)

# Write to CSV
df.to_csv("new_sample.csv", index=False)

print("CSV file 'new_sample.csv' has been created successfully!")