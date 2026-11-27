"""Issue a teacher signup invitation only after operator verification.

Usage: python scripts/create-teacher-invitation.py --email teacher@example.test --verified-by operator-reference
The one-time secret is printed for manual delivery; never commit or attach its output.
"""
import argparse
import hashlib
import re
import secrets
import uuid
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('environment', Path(__file__).with_name('verify-environment.py'))
environment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(environment)

def quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--email', required=True)
    parser.add_argument('--verified-by', required=True, help='External verification record/operator reference')
    args = parser.parse_args()
    email = args.email.strip().lower()
    if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', email) or len(email) > 254:
        parser.error('Invalid email')
    if not 1 <= len(args.verified_by) <= 120 or any(ord(c) < 32 for c in args.verified_by):
        parser.error('Invalid verification reference')
    raw = secrets.token_hex(32)
    digest = hashlib.sha256(raw.encode()).hexdigest()
    invitation = str(uuid.uuid4())
    environment.sql('INSERT INTO identity.teacher_signup_invitations(id,normalized_email,digest,verified_by,expires_at) VALUES ('
                    + ','.join(map(quote, [invitation, email, digest, args.verified_by])) + ",now()+interval '24 hours')")
    print('Invitation ID:', invitation)
    print('Expires in 24 hours. Deliver this code privately once; do not save it in Jira/Git/Drive:')
    print(raw)

if __name__ == '__main__':
    main()
