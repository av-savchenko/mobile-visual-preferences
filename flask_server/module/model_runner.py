from module import object_detector
# from module import scene_recognizer
from module.utils import Error

import cv2 as cv
from io import BytesIO
from PIL import Image
import numpy as np
import base64


def decode_image(bytestring):
    try:
        img = Image.open(BytesIO(base64.b64decode(bytestring)))
        img.save('img.jpeg')
        return cv.cvtColor(np.array(img), cv.COLOR_BGR2RGB)
    except Exception as ex:
        print(ex)
        raise Error('Could not read image')


def detect_objects(image):
    results = {}
    output_dict = object_detector.detect_objects(image)
    results['num_detections'] = round(output_dict['num_detections'].tolist()[0])
    results['detection_boxes'] = output_dict['detection_boxes'].tolist()
    results['detection_scores'] = output_dict['detection_scores'].tolist()
    results['detection_classes'] = [object_detector.category_index[x] for x in output_dict['detection_classes']]
    return results


def detect_faces(image):
    pass


def recognize_scene(image):
    res = scene_recognizer.scenes_func(image).tolist()
    return [{"id": scene_recognizer.label_to_class[i], "score": res[i]} for i in range(len(res)) if res[i] > 0.1]
