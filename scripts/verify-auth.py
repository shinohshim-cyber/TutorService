"""Authentication/relationship integration checks using synthetic, isolated fixtures.

Run after Compose is healthy. Stops only this project's auth Redis for one fault test,
restores it in finally, and deletes only UUID fixtures created by this run.
Never prints credentials, tokens, email addresses or HTTP bodies.
"""
import concurrent.futures
import hashlib
import http.cookiejar
import http.client
import importlib.util
import json
from pathlib import Path
import secrets
import threading
import urllib.error
import urllib.request
import uuid

spec = importlib.util.spec_from_file_location('environment', Path(__file__).with_name('verify-environment.py'))
env = importlib.util.module_from_spec(spec)
spec.loader.exec_module(env)
BASE = 'http://127.0.0.1:5173/api/v1'
ORIGIN = 'http://127.0.0.1:5173'
PREFIX = 'auth-' + uuid.uuid4().hex
PASSWORD = secrets.token_hex(16)
USER_IDS = []
INVITE_IDS = []
FAMILIES = set()
EMAILS = []

class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.access = ''
        self.csrf = self.call('GET', '/auth/csrf')[1]['token']

    def call(self, method, path, body=None, expected=200, *, csrf=True, origin=ORIGIN, bearer=None, cookie=None):
        if method != 'GET' and csrf and cookie is None:
            self.csrf = self.call('GET', '/auth/csrf')[1]['token']
        headers = {'Origin': origin} if origin is not None else {}
        if method != 'GET' and csrf:
            headers['X-XSRF-TOKEN'] = self.csrf
        token = self.access if bearer is None else bearer
        if token and not path.startswith('/auth/'):
            headers['Authorization'] = 'Bearer ' + token
        if cookie is not None:
            headers['Cookie'] = cookie
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        status = response.code
        payload = response.read()
        parsed = json.loads(payload) if payload else None
        assert status == expected, f'{method} {path}: expected {expected}, got {status} ({parsed.get("code", "response") if isinstance(parsed, dict) else "response"})'
        if status >= 400:
            assert set(['code', 'message', 'traceId', 'details']) <= set(parsed), 'Unsafe error contract'
        return response, parsed

    def login(self, email, password=PASSWORD):
        response, body = self.call('POST', '/auth/login', {'email': email, 'password': password})
        self.access = body['accessToken']
        self.csrf = self.call('GET', '/auth/csrf')[1]['token']
        cookie = next(c.value for c in self.jar if c.name == 'TUTOR_REFRESH')
        FAMILIES.add(cookie[:36])
        assert 'HttpOnly' in response.headers['Set-Cookie'] and 'SameSite=Strict' in response.headers['Set-Cookie']
        assert 'Path=/api/v1/auth' in response.headers['Set-Cookie']
        return body

    def refresh_cookie(self):
        return next(c.value for c in self.jar if c.name == 'TUTOR_REFRESH')

def email(tag):
    value = PREFIX + '-' + tag + '@example.test'
    EMAILS.append(value)
    return value

def signup(client, address, **extras):
    _, profile = client.call('POST', '/auth/signup', {'email': address, 'password': PASSWORD, 'name': '가상 회원', **extras}, 201)
    USER_IDS.append(profile['id'])
    return profile

def invitation(address):
    raw = secrets.token_hex(32)
    identifier = str(uuid.uuid4())
    INVITE_IDS.append(identifier)
    digest = hashlib.sha256(raw.encode()).hexdigest()
    # All interpolated identifiers are generated locally; email consists only of fixed text and UUID.
    env.sql("INSERT INTO identity.teacher_signup_invitations(id,normalized_email,digest,verified_by,expires_at) VALUES ('"
            + identifier + "','" + address + "','" + digest + "','integration-fixture',now()+interval '24 hours')")
    return raw

def main():
    student, teacher, outsider = Client(), Client(), Client()
    try:
        student_email, teacher_email, outsider_email = email('student'), email('teacher'), email('outsider')
        student.call('POST', '/auth/signup', {'email': student_email, 'password': '가'*25, 'name': '테스트'}, 400)
        student.call('POST', '/auth/signup', {'email': student_email, 'password': 'short', 'name': '테스트'}, 400)
        student.call('POST', '/auth/signup', {'email': student_email, 'password': PASSWORD, 'name': '테스트', 'requestedRole': 'TEACHER'}, 403)
        s = signup(student, student_email.upper())
        assert s['email'] == student_email and s['role'] == 'STUDENT'
        student.call('POST', '/auth/signup', {'email': student_email, 'password': PASSWORD, 'name': '중복'}, 409)
        teacher_code = invitation(teacher_email)
        t = signup(teacher, teacher_email, requestedRole='TEACHER', teacherInvitationToken=teacher_code)
        outsider.call('POST', '/auth/signup', {'email': teacher_email, 'password': PASSWORD, 'name': '재사용', 'requestedRole': 'TEACHER', 'teacherInvitationToken': teacher_code}, 403)
        outsider.call('POST', '/auth/signup', {'email': email('reused'), 'password': PASSWORD, 'name': '테스트', 'requestedRole': 'TEACHER', 'teacherInvitationToken': teacher_code}, 403)
        o = signup(outsider, outsider_email)
        assert env.sql("SELECT bool_and(password_hash LIKE '$2a$12$%' OR password_hash LIKE '$2b$12$%') FROM identity.users WHERE id IN ('" + "','".join(USER_IDS) + "')") == 't'
        print('PASS AUTH-01: signup, normalized unique email, BCrypt, password bytes and teacher verification')

        student.login(student_email); teacher.login(teacher_email); outsider.login(outsider_email)
        student.call('GET', '/users/me', expected=401, bearer='invalid')
        student.call('PATCH', '/users/me', {'name': '수정 회원', 'role': 'TEACHER'}, 400)
        student.call('PATCH', '/users/me', {'name': '수정 회원'}, csrf=False, expected=403)
        student.call('PATCH', '/users/me', {'name': '수정 회원'}, origin='https://attacker.example', expected=403)
        student.call('PATCH', '/users/me', {'name': '수정 회원'}, origin=None, expected=403)
        student.call('PATCH', '/users/me', {'name': 'a' * 17000}, expected=413)
        direct = http.client.HTTPConnection('127.0.0.1', 18080, timeout=10)
        try:
            direct.request('POST', '/api/v1/auth/signup', body=iter([b'x' * 9000, b'x' * 9000]), headers={'Origin': ORIGIN, 'Content-Type': 'application/json'}, encode_chunked=True)
            oversized = direct.getresponse()
            assert oversized.status == 413 and json.loads(oversized.read())['code'] == 'PAYLOAD_TOO_LARGE'
        finally:
            direct.close()
        assert student.call('PATCH', '/users/me', {'name': '수정 회원'})[1]['name'] == '수정 회원'
        print('PASS AUTH-03: bearer rejection, profile field allowlist, CSRF and Origin enforcement')

        student.call('POST', '/relationships/invitations', {'recipientId': o['id']}, 403)
        student.call('GET', '/relationships?limit=101', expected=400)
        student.call('GET', '/relationships?cursor=invalid', expected=400)
        invite = teacher.call('POST', '/relationships/invitations', {'recipientId': s['id']}, 201)[1]
        extra_invite = teacher.call('POST', '/relationships/invitations', {'recipientId': s['id']}, 201)[1]
        first_page = student.call('GET', '/relationships/invitations?limit=1')[1]
        assert first_page['hasMore'] and first_page['nextCursor']
        next_page = student.call('GET', '/relationships/invitations?limit=1&cursor=' + first_page['nextCursor'])[1]
        assert len(next_page['items']) == 1 and not next_page['hasMore']
        assert first_page['items'][0]['id'] != next_page['items'][0]['id']
        outsider.call('POST', '/relationships/invitations/' + invite['id'] + '/accept', expected=404)
        assert any(i['id'] == invite['id'] for i in student.call('GET', '/relationships/invitations')[1]['items'])
        relation = student.call('POST', '/relationships/invitations/' + invite['id'] + '/accept')[1]
        student.call('POST', '/relationships/invitations/' + invite['id'] + '/accept', expected=409)
        student.call('POST', '/relationships/invitations/' + extra_invite['id'] + '/accept', expected=409)
        outsider.call('DELETE', '/relationships/' + relation['id'], expected=404)
        assert len(student.call('GET', '/relationships')[1]['items']) == 1
        student.call('DELETE', '/relationships/' + relation['id'], expected=204)
        assert not teacher.call('GET', '/relationships')[1]['items']
        expired = teacher.call('POST', '/relationships/invitations', {'recipientId': s['id']}, 201)[1]
        env.sql("UPDATE identity.relationship_invitations SET expires_at=now()-interval '1 minute' WHERE id='" + expired['id'] + "'")
        student.call('POST', '/relationships/invitations/' + expired['id'] + '/accept', expected=409)
        print('PASS COM-03: recipient scope, single-use invitation, expiration, relationship authorization/revocation')

        old = student.refresh_cookie()
        original_ttl = int(env.redis('redis', 'TTL', 'tutor:auth:family:' + old[:36]))
        student.call('POST', '/auth/refresh', csrf=False, expected=403)
        _, rotated = student.call('POST', '/auth/refresh')
        student.access = rotated['accessToken']
        assert student.refresh_cookie() != old
        assert 0 < int(env.redis('redis', 'TTL', 'tutor:auth:family:' + old[:36])) <= original_ttl
        family_dump = env.redis('redis', 'HGETALL', 'tutor:auth:family:' + old[:36])
        assert old not in family_dump and student.refresh_cookie() not in family_dump
        override = 'TUTOR_REFRESH=' + old + '; XSRF-TOKEN=' + student.csrf
        student.call('POST', '/auth/refresh', expected=401, cookie=override)
        student.call('GET', '/users/me', expected=401)
        student.call('POST', '/auth/refresh', expected=401)
        print('PASS AUTH-02: opaque token rotation, no raw Redis storage and replay revokes entire family/access')

        student.login(student_email)
        shared_cookie = 'TUTOR_REFRESH=' + student.refresh_cookie() + '; XSRF-TOKEN=' + student.csrf
        barrier = threading.Barrier(2)
        def race():
            request = urllib.request.Request(BASE + '/auth/refresh', method='POST', headers={'Origin': ORIGIN, 'X-XSRF-TOKEN': student.csrf, 'Cookie': shared_cookie})
            barrier.wait(timeout=10)
            try:
                with urllib.request.urlopen(request, timeout=15) as response:
                    return response.status, json.load(response)
            except urllib.error.HTTPError as error:
                return error.code, json.load(error)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(race) for _ in range(2)]
            results = [f.result() for f in futures]
        assert sorted(status for status, _ in results) == [200, 401]
        winner = next(body['accessToken'] for status, body in results if status == 200)
        student.call('GET', '/users/me', bearer=winner, expected=401)
        print('PASS AUTH-02: simultaneous refresh consumes once and replay invalidates winning access')

        student.login(student_email)
        family = student.refresh_cookie()[:36]
        env.redis('redis', 'HSET', 'tutor:auth:family:' + family, 'exp', '0')
        student.call('POST', '/auth/refresh', expected=401)
        student.login(student_email)
        student.call('POST', '/auth/logout', expected=204)
        student.call('GET', '/users/me', expected=401)
        student.call('POST', '/auth/refresh', expected=401)
        print('PASS AUTH-02: absolute expiration and logout immediately block access/refresh')

        student.login(student_email)
        final_invite = teacher.call('POST', '/relationships/invitations', {'recipientId': s['id']}, 201)[1]
        student.call('POST', '/relationships/invitations/' + final_invite['id'] + '/accept')
        second = Client(); second.login(student_email)
        student.call('DELETE', '/users/me', {'password': 'wrong-password'}, expected=401)
        assert student.call('DELETE', '/users/me', {'password': PASSWORD}, 202)[1]['deletionId']
        student.call('GET', '/users/me', expected=401)
        second.call('GET', '/users/me', expected=401)
        second.call('POST', '/auth/refresh', expected=401)
        second.call('POST', '/auth/login', {'email': student_email, 'password': PASSWORD}, expected=401)
        assert not teacher.call('GET', '/relationships/invitations')[1]['items']
        assert not teacher.call('GET', '/relationships')[1]['items']
        assert env.sql("SELECT status='DELETED' AND session_version=1 AND password_hash='DELETED' AND normalized_email LIKE '%@deleted.invalid' FROM identity.users WHERE id='" + s['id'] + "'") == 't'
        print('PASS COM-01: reauthentication, deletion anonymization and all-session invalidation')

        limited_email = email('limited')
        for _ in range(10):
            outsider.call('POST', '/auth/login', {'email': limited_email, 'password': 'wrong-password'}, expected=401)
        outsider.call('POST', '/auth/login', {'email': limited_email, 'password': 'wrong-password'}, expected=429)
        print('PASS AUTH-01: login rate limit')

        try:
            env.command('stop', 'redis')
            teacher.call('GET', '/users/me', expected=503)
            teacher.call('POST', '/auth/refresh', expected=503)
            teacher.call('POST', '/auth/login', {'email': teacher_email, 'password': PASSWORD}, expected=503)
        finally:
            env.command('up', '-d', '--wait', '--wait-timeout', '90', 'redis')
        teacher.call('GET', '/users/me')
        print('PASS AUTH-02: Redis outage fails closed and service recovers')
        logs = env.command('logs', '--no-color', 'backend', 'postgres')
        sensitive = [PREFIX, PASSWORD, teacher_code, old, student.access, teacher.access, outsider.access]
        assert all(value and value not in logs for value in sensitive), 'Sensitive fixture data leaked in service logs'
        print('PASS SECURITY: member identifiers, passwords, invitation codes and tokens absent from application/database logs')
    finally:
        # Delete only records created by this test. Never flush Redis or reset volumes.
        for family in FAMILIES:
            uuid.UUID(family)
            env.redis('redis', 'DEL', 'tutor:auth:family:' + family)
        if USER_IDS:
            for identifier in USER_IDS:
                uuid.UUID(identifier)
            ids = "'" + "','".join(USER_IDS) + "'"
            env.sql('DELETE FROM identity.teacher_student_relations WHERE teacher_id IN (' + ids + ') OR student_id IN (' + ids + ');'
                    + 'DELETE FROM identity.relationship_invitations WHERE sender_id IN (' + ids + ') OR recipient_id IN (' + ids + ');'
                    + 'DELETE FROM identity.account_deletions WHERE user_id IN (' + ids + ');'
                    + 'DELETE FROM identity.users WHERE id IN (' + ids + ')')
        if INVITE_IDS:
            env.sql("DELETE FROM identity.teacher_signup_invitations WHERE id IN ('" + "','".join(INVITE_IDS) + "')")

if __name__ == '__main__':
    main()
