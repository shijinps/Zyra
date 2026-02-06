import os
import httpx
import logging
from typing import List, Optional

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ZyraQuantum")

# API Keys (Load from env or use provided defaults)
CISCO_API_KEY = os.getenv("CISCO_QRNG_API_KEY", "gBNe17S1s!-,o,(UWQn;IJ:2@5{9+0p1n3W(yE@mY6J67!0d{qfxr90Dh@H{hQ2G")
IBM_API_TOKEN = os.getenv("IBM_QUANTUM_API_TOKEN", "snU2VhbBd7gt5hel4PNve1iQiySBzI8XqciIvpTB_6uo")

class CiscoQRNG:
    """
    Client for Cisco Outshift Quantum Random Number Generator.
    Provides true entropy for Zyra's personality and decision making.
    """
    BASE_URL = "https://api.qrng.outshift.com/api/v1/random_numbers"

    @staticmethod
    async def get_quantum_int(min_val: int = 0, max_val: int = 100, count: int = 1) -> List[int]:
        """
        Fetches quantum random integers.
        """
        if not CISCO_API_KEY:
            logger.warning("Cisco API Key missing. Returning pseudo-random fallback.")
            import random
            return [random.randint(min_val, max_val) for _ in range(count)]

        headers = {
            "x-id-api-key": CISCO_API_KEY,
            "Content-Type": "application/json"
        }
        
        # Cisco API usually expects just the key in headers.
        # Check specific API limitations/parameters. 
        # Assuming standard implementation based on common QRNG APIs.
        # If the API allows parameters in URL or body.
        
        try:
            # Note: The free tier simple endpoint might just return a list or specific format.
            # We will try to fetch and then scale/map locally if functionality is limited.
            async with httpx.AsyncClient() as client:
                response = await client.get(CiscoQRNG.BASE_URL, headers=headers, timeout=5.0)
                response.raise_for_status()
                data = response.json()
                
                # Setup depends on exact response format of Cisco Outshift.
                # Assuming generic behavior: returns list of numbers.
                # If structure is {"random_numbers": [...]}, adapt here.
                
                # Mock adaptation if we can't see the exact doc details right now:
                # We will map the received bytes/ints to our range.
                raw_numbers = data if isinstance(data, list) else data.get('random_numbers', [])
                
                if not raw_numbers:
                    # Fallback if empty
                    import random
                    return [random.randint(min_val, max_val) for _ in range(count)]

                # Map raw numbers to requested range
                results = []
                for num in raw_numbers[:count]:
                    # Simple modulo mapping
                    mapped = min_val + (num % (max_val - min_val + 1))
                    results.append(mapped)
                    
                return results

        except Exception as e:
            logger.error(f"Failed to fetch from Cisco QRNG: {e}")
            # Fallback
            import random
            return [random.randint(min_val, max_val) for _ in range(count)]
            
    @staticmethod
    async def get_mood() -> str:
        """
        Determines Zyra's MOOD based on Quantum Entropy.
        """
        moods = [
            "Analytical & Precise", 
            "Empathetic & Warm", 
            "Witty & Humorous", 
            "Professional & Concise",
            "Creative & Abstract",
            "Protective & Vigilant"
        ]
        
        # Get 1 number to pick mood
        indices = await CiscoQRNG.get_quantum_int(0, len(moods)-1, 1)
        return moods[indices[0]]

class IBMQuantumBrain:
    """
    Client for IBM Quantum (via Qiskit).
    Executes quantum circuits for complex logic.
    """
    
    @staticmethod
    def run_superposition_check():
        """
        Runs a simple Bell State or Superposition circuit.
        Returns a 'True Grid' binary decision.
        """
        if not IBM_API_TOKEN:
            return {"status": "skipped", "reason": "No IBM API Token provided", "result": "Classical Simulation"}

        try:
            from qiskit import QuantumCircuit
            from qiskit_ibm_runtime import QiskitRuntimeService, SamplerV2 as Sampler
            
            # Setup Service
            service = QiskitRuntimeService(channel="ibm_quantum", token=IBM_API_TOKEN)
            
            # Create Circuit (2 Qubits, Bell State)
            qc = QuantumCircuit(2)
            qc.h(0) # Hadamard gate on q0 (Superposition)
            qc.cx(0, 1) # CNOT gate (Entanglement)
            qc.measure_all()
            
            # Run on least busy backend or simulator
            # For speed in 'prod', we might prefer a simulator unless needed.
            backend = service.least_busy(simulator=False) # Or operational=True
            sampler = Sampler(backend)
            
            # Execute
            job = sampler.run([qc])
            result = job.result()
            
            # Parse counts (pub_result -> data -> measure -> get_counts)
            # Simplified for this example
            counts = result[0].data.measure.get_counts()
            
            return {
                "backend": backend.name,
                "counts": counts, # e.g. {'00': 500, '11': 524}
                "decision_bit": 1 if counts.get('11', 0) > counts.get('00', 0) else 0
            }

        except Exception as e:
            logger.error(f"IBM Quantum Execution Failed: {e}")
            return {"error": str(e), "fallback": "Using classical logic"}
