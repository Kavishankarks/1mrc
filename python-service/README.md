# Python HTTP Server for 1MRC

This is a Python implementation of the 1MRC service, added as a separate spin to the original 1MRC project￼.

It exposes a simple TCP/HTTP server that:
- Handles POST /event requests with JSON bodies containing userId and value.
- Handles GET /stats requests to return metrics:
- totalRequests
- uniqueUsers
- sum
- avg

## Setup
Create a virtual environment:
```bash
python3 -m venv .venv
```

Activate the environment:
```bash
source .venv/bin/activate
```

Install dependencies:
```bash
pip install -r requirements.txt
```

Start the server:
```bash
python main.py
```

## Performance Notes
- This Python implementation is single-process and async.
- Unfortunately, it is not optimised for high concurrency:
- The server cannot handle 1000 concurrent requests reliably.
- Maximum sustainable-ish throughput is roughly 100 concurrent requests under stress.

For comparison, similar Go-based implementations in the original 1MRC repo can reach >60,000 requests/sec.

## License
This code is provided under the same license as the original 1MRC repository.