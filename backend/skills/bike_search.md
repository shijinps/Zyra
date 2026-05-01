# Skill: Vehicle Lookup via Cloud Android

## Description
This skill allows the agent to search for vehicle registration details (such as the owner's name and registration authority) by sending commands to the Zyra ADB Bridge. 
The bridge controls a Cloud Android instance running a vehicle lookup application.

## Trigger
Use this skill when the user asks to "find the owner of bike number", "check vehicle registration", or provides a license plate number like "KL-07-CD-1234".

## Tools Required
- **http_request**: To communicate with the Zyra ADB Bridge (`http://localhost:8080`).

## Execution Steps

1. **Format the Input**
   Extract the vehicle license plate from the user's prompt. Remove any hyphens or spaces so it forms a single string (e.g., `KL07CD1234`).

2. **Launch the Target Application**
   Make a POST request to `http://<adb_bridge_host>:8080/action/launch`.
   Payload:
   ```json
   {
     "package_name": "com.vahan.vehicleinfo",
     "activity_name": ""
   }
   ```

3. **Input the License Plate**
   Wait 3 seconds for the app to load. Then make a POST request to `http://<adb_bridge_host>:8080/action/type` to enter the plate number.
   Payload:
   ```json
   {
     "text": "KL07CD1234"
   }
   ```

4. **Trigger Search**
   Make a POST request to `http://<adb_bridge_host>:8080/action/tap` targeting the coordinates of the "Search" button.
   Payload:
   ```json
   {
     "x": 500,
     "y": 1200
   }
   ```

5. **Read Results (Screenshot / OCR)**
   Wait 5 seconds for results to load. Make a GET request to `http://<adb_bridge_host>:8080/action/screenshot`. Process the text returned from the screenshot OCR to find the Owner Name and RTO Details.

6. **Return Summary**
   Respond to the user with a concise summary of the extracted vehicle information.
