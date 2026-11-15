from http.server import BaseHTTPRequestHandler, HTTPServer
import json, urllib.request

history = {}

class H(BaseHTTPRequestHandler):
    def do_POST(self):
        key = self.headers.get("X-Key", "")
        n = int(self.headers.get("Content-Length", 0))
        text = self.rfile.read(n).decode("utf-8")
        msgs = history.setdefault(key, [])
        msgs.append({"role": "user", "content": text})
        body = json.dumps({
            "model": "claude-sonnet-5-5",
            "max_tokens": 400,
            "system": "Reply briefly in plain text. No markdown.",
            "messages": msgs[-9:]
        }).encode()
        req = urllib.request.Request(
            "https://api.anthropic.com/v1/messages", body,
            {"x-api-key": key, "anthropic-version": "2023-06-01",
             "content-type": "application/json"})
        try:
            r = json.load(urllib.request.urlopen(req, timeout=60))
            reply = r["content"][0]["text"]
            msgs.append({"role": "assistant", "content": reply})
        except Exception as e:
            msgs.pop()
            reply = "Error: %s" % e
        out = reply.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

HTTPServer(("0.0.0.0", 8080), H).serve_forever()
