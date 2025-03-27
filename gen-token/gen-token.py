import sys
import jwt
import time

if len(sys.argv) != 2:
  print("usage: python3 gen-token.py <secret>")
  sys.exit(1)

secret=sys.argv[1]

payload = { 
    "sub": "user123",
    "exp": time.time() + 3600 
}

token = jwt.encode(payload, secret, algorithm="HS256")
print(token)

