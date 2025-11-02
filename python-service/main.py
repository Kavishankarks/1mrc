import re
import orjson
import signal
import asyncio

from multiprocessing import Value


# Global variables
PORT = 8080
HOST = "127.0.0.1"
REQ_RE_LEN = re.compile(
    rb"(?i)^Content-Length:\s*(\d+)",
    re.MULTILINE,
)
HTTP_200_PREF = b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: "
HTTP_404 = (
    b"HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 9\r\n\r\nNot Found"
)
HTTP_500 = b"HTTP/1.1 500 Internal Server Error\r\nConnection: close\r\nContent-Length: 21\r\n\r\nInternal Server Error"

# Atomic counters
req_total = Value("i", 0)
req_sum = Value("f", 0)
NUM_SHARDS = 1024
shards = [set() for _ in range(NUM_SHARDS)]
locks = [asyncio.Lock() for _ in range(NUM_SHARDS)]


def shard_index(user_id):
    """Determine the shard index for a given user ID."""
    return hash(user_id) % NUM_SHARDS


async def add_unique_user(user_id):
    """Add a user ID to the appropriate shard."""
    i = shard_index(user_id)
    async with locks[i]:
        shards[i].add(user_id)


def unique_count():
    """Count unique users across all shards."""
    return sum(len(s) for s in shards)


async def handle(reader, writer):
    """Handle incoming HTTP requests."""
    global req_sum, req_total

    try:
        data_bin = await reader.read(2048)

        # Handle GET /stats
        if b"GET /stats " in data_bin:
            body_response = f'{{"totalRequests":{req_total.value},"uniqueUsers":{unique_count()},"sum":{req_sum.value},"avg":{req_sum.value/req_total.value  if req_total.value else 0.0}}}'
            response = (
                HTTP_200_PREF
                + f"{len(body_response)}".encode()
                + b"\r\n\r\n"
                + body_response.encode()
            )

        # POST /event
        elif b"POST /event " in data_bin:
            try:
                # Split headers and existing body bytes
                _, body_sofar = data_bin.split(b"\r\n\r\n")

                # Content-Length
                m = REQ_RE_LEN.search(data_bin)
                content_length = int(m.group(1).decode()) if m else 0

                # Read remaining bytes
                remaining = content_length - len(body_sofar)
                if remaining > 0:
                    body_sofar += await reader.read(remaining)

                body = orjson.loads(body_sofar.decode())
                await add_unique_user(body.get("userId"))
                req_sum.value += float(body.get("value", 0))
                req_total.value += 1

                body_response = f'{{"totalRequests":{req_total.value},"uniqueUsers":{unique_count()},"sum":{req_sum.value},"avg":{req_sum.value/req_total.value}}}'
                response = (
                    HTTP_200_PREF
                    + f"{len(body_response)}".encode()
                    + b"\r\n\r\n"
                    + body_response.encode()
                )

            except Exception:
                response = HTTP_500

        else:
            response = HTTP_404

    except Exception:
        response = HTTP_500

    finally:
        writer.write(response)
        await writer.drain()
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass


async def start_server(shutdown_event):
    """Start the asynchronous HTTP server."""
    server = await asyncio.start_server(
        handle,
        HOST,
        PORT,
        reuse_address=True,
        reuse_port=True,
    )
    print(f"Listening on http://{HOST}:{PORT}")
    async with server:
        await shutdown_event.wait()
        print("\nShutting down gracefully...")
        server.close()
        await server.wait_closed()
        print("Server stopped.")


if __name__ == "__main__":
    """Main entry point for the server."""
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    shutdown_event = asyncio.Event()

    # Graceful signal handling
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, shutdown_event.set)
        except NotImplementedError:
            signal.signal(sig, lambda s, f: shutdown_event.set())

    try:
        loop.run_until_complete(start_server(shutdown_event))
    finally:
        loop.close()
