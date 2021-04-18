import flask
import sys
import os
from module import constants
from werkzeug.security import generate_password_hash


if 'OBJECT_DETECTION_DIR' in os.environ:
    OBJECT_DETECTION_DIR = os.environ['OBJECT_DETECTION_DIR']
else:
    print('env var OBJECT_DETECTION_DIR is not set. Use default')
    OBJECT_DETECTION_DIR = constants.DEFAULT_OBJECT_DETECTION_DIR
print(OBJECT_DETECTION_DIR)
sys.path.append(OBJECT_DETECTION_DIR)
sys.path.append(OBJECT_DETECTION_DIR + '/..')
sys.path.append('../..')
sys.path.append('../../detection')
sys.path.append('../../imagenet/_production_')  # uses paths relative to pwd!
sys.path.append('../../facial_analysis')

app = flask.Flask(__name__)

from utils import label_map_util
from detect_objects import ObjectDetector
# from scene_analysis import get_scene_recognizer

object_detector = ObjectDetector(mostAccurate=True)
# scene_recognizer = get_scene_recognizer(mostAccurate=True)

server_session = {
    'tokens': {}
}

server_session['tokens'][constants.DEV_ACCESS_TOKEN] = {
    'user': 'dev'
}

from module import routes