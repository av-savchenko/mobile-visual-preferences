from module import app
from module import server_session

from module import utils
from module import model_runner
import flask
from werkzeug.security import generate_password_hash


@app.route('/', methods=["GET"])
def index():
    return "Server is running"


'''
authenticates a user
'''


@app.route('/authenticate', methods=["POST"])
def authenticate():
    try:
        resp = {'success': False}
        request = flask.request
        payload = utils.try_get_json(request)
        if payload['user'] is None or payload['secret'] is None:
            raise utils.Error('Username or Password is missing')
        generated_token = generate_password_hash(payload['secret'])
        server_session['tokens'][generated_token] = {
            'user': payload['user']
        }

        utils.log('created user %s with token: %s from secret %s' %
                  (payload['user'],
                   generated_token,
                   payload['secret']))
        resp['token'] = generated_token
        resp['success'] = True
        return flask.jsonify(resp)
    except Exception as ex:
        print(ex)
        raise utils.Error("ex")


'''
processes a photo sent to the server
'''


@app.route('/predict', methods=["POST"])
@utils.require_api_token
def predict():
    try:
        payload = utils.try_get_json(flask.request)
        if 'img' not in payload:
            raise utils.Error("Image not in the request", "ERROR")
        resp = {
            'success': False,
            'data': {
                'scenes': [],
                'detections': {
                    'objects': []
                }
            }
        }

        picture = model_runner.decode_image(payload['img'])
        resp['data']['detections']['objects'] = model_runner.detect_objects(picture)
        # resp['data']['scenes'] = model_runner.recognize_scene(picture)
        resp['success'] = True
        return flask.jsonify(resp)
    except Exception as ex:
        print(ex)
        raise utils.Error("predict error")

'''
error handler
'''


@app.errorhandler(utils.Error)
def handle_invalid_usage(error):
    response = flask.jsonify(error.to_dict())
    response.status_code = error.status_code
    utils.log(error.message, 'ERROR')
    return response
