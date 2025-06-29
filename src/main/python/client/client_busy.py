import socket
import threading

def simple_http_request(i):
    req = b"GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
    try:
        with socket.create_connection(("localhost", 8080), timeout=2) as s:
            s.sendall(req)
            resp = s.recv(4096)
            print(f"Thread {i}: Response received.")
    except Exception as e:
        print(f"Thread {i}: Exception: {e}")

if __name__ == '__main__':
    threads = []
    for i in range(20):
        t = threading.Thread(target=simple_http_request, args=(i,))
        threads.append(t)
        t.start()
    for t in threads:
        t.join()
