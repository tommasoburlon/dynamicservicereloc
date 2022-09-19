from http.server import BaseHTTPRequestHandler, HTTPServer
from netifaces import interfaces, ifaddresses, AF_INET
import os

"""
	Simple HTTP server application, the server is
	hosted on the port 8000, the application
	accept get request returning the ip of the server
"""

localIP = os.getenv('IP_ADDR')
serviceIP = os.getenv('SERVICE_IP')

class minimalServer(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()

        message = str(localIP)
        self.wfile.write(bytes(message, "utf8"))
        return

def run():
    if localIP != None:
        server_address = (localIP, 8000)
    else:
        server_address = ("127.0.0.1", 8000)
    httpd = HTTPServer(server_address, minimalServer)
    httpd.serve_forever()

if __name__ == '__main__':
    run()


