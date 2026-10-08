"""Create local debug credentials. Never overwrite an existing configuration."""
import argparse
from pathlib import Path
import base64
import hashlib
import secrets

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--presentation", action="store_true", help="Create the separate public demonstration credentials")
args = parser.parse_args()
filename = "presentation-admin.properties" if args.presentation else "admin-demo.properties"
path = Path(__file__).resolve().parents[1] / ".local" / filename
if not path.exists():
    path.parent.mkdir(parents=True, exist_ok=True)
    password = "BloomyDemo123!" if args.presentation else secrets.token_urlsafe(24)
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac('sha256', password.encode(), salt, 210000, 32)
    with path.open('x') as file:
        path.chmod(0o600)
        file.write('email=admin@bloomy.demo\npassword=' + password + '\nsalt=' + base64.b64encode(salt).decode() + '\nhash=' + base64.b64encode(digest).decode() + '\n')
print('Debug credentials file:', path)
