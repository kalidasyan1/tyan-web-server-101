import socket

def simple_http_request(host, port):
    req = b"GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((host, port))
        s.sendall(req)
        resp = s.recv(4096)
        print(resp.decode())

if __name__ == '__main__':
    # for i in range(3):
    # print(f"\n--- Request {i+1} ---")
    simple_http_request('localhost', 8090)
