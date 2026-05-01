import re
import json

def lookup_vehicle(plate_number: str) -> str:
    """
    Looks up vehicle registration information based on the license plate.
    In a real scenario, this would query API Setu or a third-party VAHAN gateway.
    """
    # Clean the plate number
    clean_plate = re.sub(r'[^A-Z0-9]', '', plate_number.upper())
    
    # Mock data store
    db = {
        "KL07CD1234": {
            "owner_name": "S*** P S",
            "vehicle_model": "Royal Enfield Classic 350",
            "registration_date": "14-Feb-2022",
            "rto": "Ernakulam RTO (KL-07)",
            "insurance_validity": "Active (until 13-Feb-2027)"
        },
        "KL01BK2026": {
            "owner_name": "J*** D***",
            "vehicle_model": "Honda Activa 6G",
            "registration_date": "01-Jan-2024",
            "rto": "Thiruvananthapuram RTO (KL-01)",
            "insurance_validity": "Active"
        }
    }
    
    if clean_plate in db:
        return json.dumps(db[clean_plate], indent=2)
    else:
        # Generate a synthetic response for unknown numbers
        return json.dumps({
            "owner_name": "Unknown (Masked for Privacy)",
            "vehicle_model": "Unknown 2-Wheeler",
            "rto": f"RTO for {clean_plate[:4]}",
            "status": "Found in VAHAN, details limited."
        }, indent=2)
