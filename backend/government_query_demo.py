import requests
import hashlib
import json

# Configuration
API_URL = "http://127.0.0.1:8001"  # Adjust if your server runs on a different port

def sha256(data: str) -> str:
    """Compute SHA256 hash of a string."""
    return hashlib.sha256(data.encode('utf-8')).hexdigest()

def query_citizen_status(id_number: str, full_name: str):
    """
    Simulate a government query to check if a citizen has a bank account.
    
    The government knows the citizen's ID and Name.
    The bank only stores the HASH of the ID.
    The query is done by hashing the ID and checking for existence.
    """
    print(f"\n--- [GOVERNMENT] Querying Citizen Status ---")
    print(f"Target Citizen: {full_name}")
    print(f"ID Number: {id_number}")
    
    # 1. Compute the hash of the ID number
    # This is the "Privacy-Preserving Linkage" key
    id_hash = sha256(id_number)
    print(f"Computed ID Hash: {id_hash}")
    
    # 2. In a real scenario, this might be a specific API endpoint.
    # For this demo, we'll simulate it by trying to enroll again or checking a lookup endpoint.
    # Since we don't have a dedicated lookup endpoint exposed in main.py yet, 
    # let's add a simple one or simulate the logic by checking the database directly if we were the bank admin,
    # OR use the existing enrollment check logic.
    
    # Let's hit a hypothetical endpoint /api/check_status (we will add this to main.py next)
    # or just use the logic that we know: query the DB.
    
    try:
        response = requests.get(f"{API_URL}/api/admin/check_citizen/{id_hash}")
        
        if response.status_code == 200:
            data = response.json()
            if data["exists"]:
                print(f"Result: FOUND ✅")
                print(f"Citizen {full_name} (ID: {id_number}) has an account at this bank.")
                print(f"Bank Account ID (User ID): {data['user_id']}")
                print(f"Registration Time: {data['created_at']}")
            else:
                print(f"Result: NOT FOUND ❌")
                print(f"Citizen {full_name} (ID: {id_number}) does NOT have an account here.")
        else:
            print(f"Error querying bank API: {response.status_code} - {response.text}")
            
    except Exception as e:
        print(f"Connection failed: {e}")
        print("Make sure the backend server is running!")

if __name__ == "__main__":
    # Example Data (Must match what you enrolled in the App)
    # Replace these with the actual data you used in the Android App
    TARGET_ID = "045205000948" 
    TARGET_NAME = "Trần Hữu Đức"
    
    query_citizen_status(TARGET_ID, TARGET_NAME)
    
    # Example of a non-existent user
    query_citizen_status("999999999999", "NON EXISTENT USER")
