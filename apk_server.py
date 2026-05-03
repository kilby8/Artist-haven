#!/usr/bin/env python3
"""
Simple HTTP Server for Artist Haven APK Download
Usage: python3 apk_server.py [port]
Example: python3 apk_server.py 8000
"""

import http.server
import socketserver
import os
import sys
from pathlib import Path
from urllib.parse import urlparse, unquote

PORT = 8000
DOCUMENT_ROOT = os.path.dirname(os.path.abspath(__file__))

class APKServerHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DOCUMENT_ROOT, **kwargs)

    def do_GET(self):
        # Parse the request path
        parsed_path = urlparse(self.path)
        file_path = unquote(parsed_path.path).lstrip('/')

        # Handle APK download
        if file_path.startswith('apk/') or file_path.endswith('.apk'):
            requested_apk = os.path.join(DOCUMENT_ROOT, file_path)
            fallback_apk = os.path.join(DOCUMENT_ROOT, 'docs', 'downloads', 'Artist-Haven-debug.apk')

            candidate_paths = [requested_apk, fallback_apk]
            apk_full_path = None
            for candidate in candidate_paths:
                abs_candidate = os.path.abspath(candidate)
                if not abs_candidate.startswith(os.path.abspath(DOCUMENT_ROOT)):
                    continue
                if os.path.exists(abs_candidate):
                    apk_full_path = abs_candidate
                    break

            if apk_full_path is None:
                self.send_error(404, f'APK not found for request: {file_path}')
                return

            apk_name = os.path.basename(apk_full_path)
            self.send_response(200)
            self.send_header('Content-type', 'application/vnd.android.package-archive')
            self.send_header('Content-Length', os.path.getsize(apk_full_path))
            self.send_header('Content-Disposition', f'attachment; filename="{apk_name}"')
            self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
            self.end_headers()

            with open(apk_full_path, 'rb') as f:
                self.wfile.write(f.read())
            return

        # Serve the HTML file
        if file_path == '' or file_path == '/':
            file_path = 'APK_DOWNLOAD_SERVER.html'

        full_path = os.path.join(DOCUMENT_ROOT, file_path)

        # Security: prevent path traversal
        if not os.path.abspath(full_path).startswith(os.path.abspath(DOCUMENT_ROOT)):
            self.send_error(403, 'Forbidden')
            return

        if os.path.isfile(full_path):
            self.send_response(200)

            # Determine content type
            if full_path.endswith('.html'):
                content_type = 'text/html'
            elif full_path.endswith('.json'):
                content_type = 'application/json'
            elif full_path.endswith('.apk'):
                content_type = 'application/vnd.android.package-archive'
            else:
                content_type = 'application/octet-stream'

            self.send_header('Content-type', content_type)
            self.send_header('Content-Length', os.path.getsize(full_path))
            self.end_headers()

            with open(full_path, 'rb') as f:
                self.wfile.write(f.read())
        else:
            self.send_error(404, 'File not found')

    def log_message(self, format, *args):
        """Override to add custom logging"""
        print(f'[{self.log_date_time_string()}] {format % args}')

def main():
    global PORT

    if len(sys.argv) > 1:
        try:
            PORT = int(sys.argv[1])
        except ValueError:
            print(f"Invalid port number: {sys.argv[1]}")
            sys.exit(1)

    handler = APKServerHandler

    try:
        with socketserver.TCPServer(("", PORT), handler) as httpd:
            print(f"""
╔════════════════════════════════════════════════════════════════╗
║           Artist Haven APK Download Server                      ║
╠════════════════════════════════════════════════════════════════╣
║                                                                 ║
║  📱 Server running at: http://localhost:{PORT}
║                                                                 ║
║  🌐 From another device on your network:                      ║
║     http://<your-computer-ip>:{PORT}
║                                                                 ║
║  To find your computer IP:                                     ║
║  • Windows: Run 'ipconfig' and look for IPv4 Address           ║
║  • Mac/Linux: Run 'ifconfig' and look for inet address         ║
║                                                                 ║
║  📝 Make sure these files are in place:                        ║
║  • APK: app/build/outputs/apk/release/app-release.apk         ║
║  • HTML: APK_DOWNLOAD_SERVER.html                              ║
║                                                                 ║
║  Press Ctrl+C to stop the server                               ║
║                                                                 ║
╚════════════════════════════════════════════════════════════════╝
            """)
            httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n✓ Server stopped")
        sys.exit(0)
    except OSError as e:
        print(f"❌ Error: {e}")
        if "Address already in use" in str(e):
            print(f"   Port {PORT} is already in use. Try: python3 apk_server.py {PORT + 1}")
        sys.exit(1)

if __name__ == "__main__":
    main()
