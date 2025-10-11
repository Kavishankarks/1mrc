#!/usr/bin/env python3
"""
Load test client for 1MRC Rust service
Sends 1 million concurrent requests to test the service
"""

import asyncio
import aiohttp
import json
import time
import random
import string
from typing import List

class LoadTestClient:
    def __init__(self, base_url: str = "http://localhost:3000"):
        self.base_url = base_url
        self.total_requests = 0
        self.successful_requests = 0
        self.failed_requests = 0

    def generate_event(self) -> dict:
        """Generate a random event"""
        user_id = ''.join(random.choices(string.ascii_letters + string.digits, k=8))
        value = random.uniform(1.0, 1000.0)
        return {"userId": user_id, "value": value}

    async def send_event(self, session: aiohttp.ClientSession, event: dict) -> bool:
        """Send a single event"""
        try:
            async with session.post(f"{self.base_url}/event", json=event) as response:
                self.total_requests += 1
                if response.status == 200:
                    self.successful_requests += 1
                    return True
                else:
                    self.failed_requests += 1
                    return False
        except Exception as e:
            self.failed_requests += 1
            return False

    async def get_stats(self) -> dict:
        """Get current statistics"""
        async with aiohttp.ClientSession() as session:
            try:
                async with session.get(f"{self.base_url}/stats") as response:
                    if response.status == 200:
                        return await response.json()
                    else:
                        return {"error": f"HTTP {response.status}"}
            except Exception as e:
                return {"error": str(e)}

    async def run_batch(self, session: aiohttp.ClientSession, batch_size: int) -> List[bool]:
        """Run a batch of requests concurrently"""
        tasks = []
        for _ in range(batch_size):
            event = self.generate_event()
            tasks.append(self.send_event(session, event))
        
        return await asyncio.gather(*tasks, return_exceptions=True)

    async def run_load_test(self, total_requests: int = 1_000_000, batch_size: int = 1000, max_concurrent: int = 100):
        """Run the complete load test"""
        print(f"Starting load test: {total_requests:,} requests in batches of {batch_size}")
        print(f"Max concurrent batches: {max_concurrent}")
        
        start_time = time.time()
        
        # Create a semaphore to limit concurrent batches
        semaphore = asyncio.Semaphore(max_concurrent)
        
        async def run_limited_batch(session):
            async with semaphore:
                return await self.run_batch(session, batch_size)
        
        # Calculate number of batches needed
        num_batches = (total_requests + batch_size - 1) // batch_size
        
        async with aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(limit=1000, limit_per_host=1000),
            timeout=aiohttp.ClientTimeout(total=30)
        ) as session:
            # Test connectivity first
            print("Testing connectivity...")
            test_stats = await self.get_stats()
            if "error" in test_stats:
                print(f"Connection failed: {test_stats['error']}")
                return
            
            print(f"Connected! Initial stats: {test_stats}")
            
            # Run batches
            batch_tasks = []
            for i in range(num_batches):
                batch_tasks.append(run_limited_batch(session))
                
                # Progress reporting
                if (i + 1) % 10 == 0:
                    print(f"Queued {i + 1:,}/{num_batches:,} batches...")
            
            print("Executing requests...")
            await asyncio.gather(*batch_tasks)
        
        end_time = time.time()
        duration = end_time - start_time
        
        # Get final stats
        print("\nGetting final statistics...")
        final_stats = await self.get_stats()
        
        # Print results
        print(f"\n{'='*50}")
        print("LOAD TEST RESULTS")
        print(f"{'='*50}")
        print(f"Total time: {duration:.2f} seconds")
        print(f"Requests sent: {self.total_requests:,}")
        print(f"Successful: {self.successful_requests:,}")
        print(f"Failed: {self.failed_requests:,}")
        print(f"Requests/second: {self.total_requests/duration:,.2f}")
        print(f"\nServer Statistics:")
        print(json.dumps(final_stats, indent=2))

async def main():
    """Main function"""
    client = LoadTestClient()
    
    # Start with a smaller test, then scale up
    print("Running warm-up test (10,000 requests)...")
    await client.run_load_test(total_requests=10_000, batch_size=100, max_concurrent=50)
    
    # Reset counters for main test
    client.total_requests = 0
    client.successful_requests = 0
    client.failed_requests = 0
    
    print("\n" + "="*50)
    print("Starting main load test (1,000,000 requests)...")
    await client.run_load_test(total_requests=1_000_000, batch_size=1000, max_concurrent=100)

if __name__ == "__main__":
    asyncio.run(main())