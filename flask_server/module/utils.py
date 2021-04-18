from functools import wraps
from module import server_session
import datetime
import time
import json
import flask
from werkzeug.security import generate_password_hash


class Error(Exception):
    status_code = 400

    def __init__(self, message, status_code=None, payload=None):
        Exception.__init__(self)
        self.message = message
        if status_code is not None:
            self.status_code = status_code
        self.payload = payload

    def to_dict(self):
        rv = dict(self.payload or ())
        rv['message'] = self.message
        return rv


def require_api_token(func):
    @wraps(func)
    def check_token(*args, **kwargs):
        payload = try_get_json(flask.request)
        if payload["token"] is not None and payload['token'] in server_session['tokens']:
            log('user: %s authenticated' % server_session['tokens'][payload['token']]['user'])
            return func(*args, **kwargs)
        else:
            raise Error('Invalid token')

    return check_token


def try_get_json(request):
    if request.method != "POST":
        raise Error("method is not POST")
    payload = json.loads(request.data.decode("utf-8"))
    if payload is None or payload == '':
        raise Error('Payload is not JSON')
    return payload


def log(message, level='Info'):
    print('* [%s] ~ %s: %s' % (
        datetime.datetime.fromtimestamp(
            time.time()).strftime('%Y-%m-%d %H:%M:%S'),
        level,
        message))
